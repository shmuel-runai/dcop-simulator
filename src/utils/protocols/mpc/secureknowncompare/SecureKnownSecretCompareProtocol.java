package utils.protocols.mpc.secureknowncompare;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.*;

/**
 * Secure Known-Secret Compare Protocol.
 * 
 * Compares a known value (a) against a secret (b) and returns:
 *   - 1 if a < b
 *   - 0 otherwise
 * 
 * Assumption: The bits of the secret b are pre-distributed as separate secrets
 * with IDs: {bitSecretPrefix}[{i}] for i = 0 to numBits-1
 * (e.g., "r-key[0]", "r-key[1]", ... "r-key[30]")
 * 
 * Algorithm:
 *   s = numBits (typically 32)
 *   d[s] = 0, e = 0, ea = 0
 *   
 *   for i = s-1 down to 0:
 *       c[i] = a[i] XOR b[i]           // Local: if a[i]=0 then b[i], else 1-b[i]
 *       product = d[i+1] * c[i]        // MPC: SecureMultiply
 *       d[i] = d[i+1] + c[i] - product
 *       e[i] = d[i] - d[i+1]
 *       e = e + e[i]
 *       if a[i] == 1: ea = ea + e[i]
 *   
 *   invea = 1 - ea                     // Local
 *   f = e * invea                      // MPC: SecureMultiply (final)
 *   return f
 * 
 * Uses Initiator/Responder pattern with inner Responder class.
 */
public class SecureKnownSecretCompareProtocol implements IDistributedProtocol, ISecureMultiplyListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_KNOWN_SECRET_COMPARE";
    private static final int DEFAULT_NUM_BITS = 32;
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println("[KnownCompare] Agent " + agentId + ": " + msg); }
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Known-Secret Compare protocol.
     * 
     * @param manager The protocol manager
     * @param knownValue The known value to compare (a)
     * @param bitSecretPrefix The prefix for bit secret IDs (bits stored as {prefix}[{i}], e.g., "r-key[0]")
     * @param numBits Number of bits (typically 32)
     * @param resultSecretId Where to store the result
     * @param rSecretId The r-key secret ID for secure multiplications
     * @param prime The prime modulus
     * @param participants List of participant agent IDs
     * @param shareStorage Share storage
     * @param resultTag Optional tag for result storage
     * @param listener Completion callback
     * @return The protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               long knownValue,
                               String bitSecretPrefix,
                               int numBits,
                               String resultSecretId,
                               String rSecretId,
                               long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureKnownSecretCompareListener listener) {
        SecureKnownSecretCompareProtocol protocol = new SecureKnownSecretCompareProtocol(
            knownValue, bitSecretPrefix, numBits, resultSecretId, rSecretId, prime, shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        SecureMultiplyProtocol.registerFactory(manager);
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            SecureKnownSecretCompareProtocol::new,  // Initiator factory
            Responder::new);                         // Responder factory
    }
    
    // =========================================================================
    // INITIATOR STATE
    // =========================================================================
    
    private String protocolId;
    private int agentId;
    private IMessageTransport transport;
    private IShareStorage shareStorage;
    private DistributedProtocolManager manager;
    private List<Integer> participants;
    private long prime;
    private ISecureKnownSecretCompareListener listener;
    
    private long knownValue;           // The known value (a)
    private String bitSecretPrefix;    // Prefix for bit secrets
    private int numBits;               // Number of bits (s)
    private String resultSecretId;     // Where to store result
    private String rSecretId;          // R-key for secure multiplications
    private String resultTag;          // Optional tag for result storage
    
    // Current state in the bit loop
    private int currentBitIndex;       // Current bit being processed (s-1 down to 0)
    private int phase1Acks = 0;
    private int bitPhaseAcks = 0;
    private int invEaAcks = 0;
    
    // Secret IDs for intermediate values
    private String eSecretId;          // Accumulated e
    private String eaSecretId;         // Accumulated ea
    private String invEaSecretId;      // 1 - ea
    
    private boolean complete;
    private boolean successful;
    
    // Self-responder for uniform message flow
    private Responder selfResponder;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /** Default constructor for factory. */
    public SecureKnownSecretCompareProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    /** Constructor injection for initiator. */
    public SecureKnownSecretCompareProtocol(long knownValue, String bitSecretPrefix, int numBits,
                                            String resultSecretId, String rSecretId, long prime,
                                            IShareStorage shareStorage, String resultTag,
                                            ISecureKnownSecretCompareListener listener) {
        this();
        this.knownValue = knownValue;
        this.bitSecretPrefix = bitSecretPrefix;
        this.numBits = numBits;
        this.resultSecretId = resultSecretId;
        this.rSecretId = rSecretId;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.resultTag = resultTag;
        this.listener = listener;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.transport = (IMessageTransport) params.get("transport");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.participants = (List<Integer>) params.get("participants");
        
        // Set derived IDs
        this.eSecretId = protocolId + "-e";
        this.eaSecretId = protocolId + "-ea";
        this.invEaSecretId = protocolId + "-invea";
        
        // Create self-responder for uniform message flow
        createSelfResponder();
        
        // Start Phase 1
        startPhase1();
    }
    
    /**
     * Creates a self-responder instance for uniform message handling.
     */
    private void createSelfResponder() {
        selfResponder = new Responder();
        Map<String, Object> responderParams = new HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("transport", transport);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("prime", prime);
        responderParams.put("storageTag", resultTag);  // Pass resultTag for cleanup
        selfResponder.initialize(responderParams);
    }
    
    // =========================================================================
    // PHASE 1: INITIALIZATION
    // =========================================================================
    
    /**
     * Phase 1: Broadcast start message to all agents.
     * Each agent will compute c[i] for all bits and initialize d[s-1], e[s-1].
     */
    private void startPhase1() {
        debug("Phase 1: Starting - broadcasting initialization with tag=" + resultTag);
        
        KnownCompareStartMessage msg = new KnownCompareStartMessage(
            protocolId, knownValue, bitSecretPrefix, numBits, prime, resultTag, agentId
        );
        for (int pid : participants) {
            transport.sendMessage(msg, pid);
        }
    }
    
    private void onPhase1Complete() {
        debug("Phase 1 complete - all agents initialized");
        
        // Start the bit loop from bit s-2 (s-1 is already handled in phase 1)
        // d[s-1] = c[s-1], so first multiply is d[s-1] * c[s-2]
        currentBitIndex = numBits - 2;
        startBitMultiply();
    }
    
    // =========================================================================
    // PHASE 2: BIT LOOP
    // =========================================================================
    
    /**
     * Start multiplication for current bit: d[currentBitIndex+1] * c[currentBitIndex]
     */
    private void startBitMultiply() {
        if (currentBitIndex < 0) {
            // All bits processed, move to phase 3
            onBitLoopComplete();
            return;
        }
        
        debug("Phase 2: Starting multiply for bit " + currentBitIndex);
        
        String dPrevId = protocolId + "-d-" + (currentBitIndex + 1);
        String cId = protocolId + "-c-" + currentBitIndex;
        String productId = protocolId + "-dc-" + currentBitIndex;  // d[i+1] * c[i]
        
        // Start secure multiply: d[i+1] * c[i]
        SecureMultiplyProtocol.start(
            manager,
            dPrevId,    // First operand: d[i+1]
            cId,        // Second operand: c[i]
            productId,  // Result
            rSecretId,  // R-key for multiplication
            prime,
            participants,
            shareStorage,
            resultTag,  // Use resultTag for cleanup
            this        // Listener
        );
    }
    
    @Override
    public void onSecureMultiplyComplete(String multiplyProtocolId, String productSecretId) {
        // Determine which multiply completed based on the productSecretId
        if (productSecretId.contains("-dc-")) {
            // Bit multiply completed (d[i+1] * c[i])
            int bitIdx = Integer.parseInt(productSecretId.substring(productSecretId.lastIndexOf("-") + 1));
            debug("Phase 2: Multiply complete for bit " + bitIdx);
            
            // Broadcast to compute d[i], e[i], accumulate e, ea
            BitPhaseMessage msg = new BitPhaseMessage(protocolId, bitIdx, productSecretId, knownValue, prime, agentId);
            bitPhaseAcks = 0;
            for (int pid : participants) {
                transport.sendMessage(msg, pid);
            }
        } else if (productSecretId.equals(resultSecretId)) {
            // Final multiply completed
            onFinalMultiplyComplete();
        }
    }
    
    private void onBitPhaseComplete() {
        debug("Phase 2: Bit " + currentBitIndex + " complete");
        
        // Move to next bit
        currentBitIndex--;
        startBitMultiply();
    }
    
    private void onBitLoopComplete() {
        debug("Phase 2 complete - all bits processed, starting phase 3");
        startPhase3();
    }
    
    // =========================================================================
    // PHASE 3: FINAL COMPUTATION
    // =========================================================================
    
    /**
     * Phase 3: Compute invea = 1 - ea, then multiply e * invea
     */
    private void startPhase3() {
        debug("Phase 3: Broadcasting invea computation");
        
        ComputeInvEaMessage msg = new ComputeInvEaMessage(protocolId, eaSecretId, invEaSecretId, prime, agentId);
        invEaAcks = 0;
        for (int pid : participants) {
            transport.sendMessage(msg, pid);
        }
    }
    
    private void onInvEaComplete() {
        debug("Phase 3: invea computed, starting final multiply e * invea");
        
        // Final multiply: e * invea -> result
        SecureMultiplyProtocol.start(
            manager,
            eSecretId,       // e
            invEaSecretId,   // invea = 1 - ea
            resultSecretId,  // Result
            rSecretId,       // R-key for multiplication
            prime,
            participants,
            shareStorage,
            resultTag,       // Apply tag to final result
            this             // Listener
        );
    }
    
    private void onFinalMultiplyComplete() {
        debug("Protocol complete!");
        
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureKnownSecretCompareComplete(protocolId, resultSecretId);
        }
    }
    
    // =========================================================================
    // MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // Route messages to selfResponder if from self
        if (senderId == agentId && selfResponder != null) {
            if (msg instanceof KnownCompareStartMessage || 
                msg instanceof BitPhaseMessage || 
                msg instanceof ComputeInvEaMessage) {
                selfResponder.handleMessage(msg, senderId);
                return;
            }
        }
        
        // Handle acks
        if (msg instanceof KnownCompareAckMessage) {
            phase1Acks++;
            if (phase1Acks >= participants.size()) {
                onPhase1Complete();
            }
        } else if (msg instanceof BitPhaseAckMessage) {
            bitPhaseAcks++;
            if (bitPhaseAcks >= participants.size()) {
                onBitPhaseComplete();
            }
        } else if (msg instanceof ComputeInvEaAckMessage) {
            invEaAcks++;
            if (invEaAcks >= participants.size()) {
                onInvEaComplete();
            }
        }
    }
    
    // =========================================================================
    // PROTOCOL INTERFACE
    // =========================================================================
    
    @Override
    public boolean isComplete() { return complete; }
    
    @Override
    public boolean isSuccessful() { return successful; }
    
    @Override
    public String getProtocolId() { return protocolId; }
    
    @Override
    public String getProtocolType() { return PROTOCOL_TYPE; }
    
    @Override
    public Object getResult() { return successful ? resultSecretId : null; }
    
    // =========================================================================
    // RESPONDER INNER CLASS
    // =========================================================================
    
    /**
     * Responder handles computation messages on all agents.
     */
    public static class Responder implements IDistributedProtocol {
        
        private static final boolean DEBUG = false;
        private void debug(String msg) { if (DEBUG) System.out.println("[KnownCompare.R] Agent " + agentId + ": " + msg); }
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private long prime;
        private String storageTag;  // Tag for intermediate share storage (enables round-based cleanup)
        
        private boolean complete;
        private boolean successful;
        
        public Responder() {
            this.complete = false;
            this.successful = false;
        }
        
        @Override
        public void initialize(Map<String, Object> params) {
            this.protocolId = (String) params.get("protocolId");
            this.agentId = (Integer) params.get("agentId");
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
            
            Long primeObj = (Long) params.get("prime");
            this.prime = (primeObj != null) ? primeObj : 0L;
            
            // Storage tag for cleanup - defaults to protocolId if not provided
            this.storageTag = (String) params.get("storageTag");
            if (this.storageTag == null) {
                this.storageTag = protocolId;
            }
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof KnownCompareStartMessage) {
                handleStartMessage((KnownCompareStartMessage) msg, senderId);
            } else if (msg instanceof BitPhaseMessage) {
                handleBitPhaseMessage((BitPhaseMessage) msg, senderId);
            } else if (msg instanceof ComputeInvEaMessage) {
                handleComputeInvEaMessage((ComputeInvEaMessage) msg, senderId);
            }
        }
        
        /**
         * Phase 1: Initialize all c[i], and set d[s-1], e[s-1], e, ea
         */
        private void handleStartMessage(KnownCompareStartMessage msg, int senderId) {
            long knownValue = msg.getKnownValue();
            String bitPrefix = msg.getBitSecretPrefix();
            int numBits = msg.getNumBits();
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            // Update storageTag from message if provided
            String msgTag = msg.getStorageTag();
            if (msgTag != null) {
                this.storageTag = msgTag;
            }
            
            debug("Phase 1: Initializing with knownValue=" + knownValue + ", numBits=" + numBits + ", tag=" + storageTag);
            
            // Compute c[i] for all bits: c[i] = a[i] XOR b[i]
            // If a[i] = 0: c[i] = b[i]
            // If a[i] = 1: c[i] = 1 - b[i]
            // Note: bits are stored as {prefix}[{i}] (e.g., "r-key[0]")
            for (int i = 0; i < numBits; i++) {
                int aBit = (int) ((knownValue >> i) & 1);
                String bBitId = bitPrefix + "[" + i + "]";
                String cId = protocolId + "-c-" + i;
                
                Share bShare = shareStorage.getShare(bBitId);
                if (bShare == null) {
                    debug("ERROR: Missing bit share " + bBitId);
                    continue;
                }
                
                Share cShare;
                if (aBit == 0) {
                    // c[i] = b[i]
                    cShare = bShare;
                } else {
                    // c[i] = 1 - b[i]
                    cShare = bShare.oneMinus(msgPrime);
                }
                shareStorage.storeShare(cId, cShare, storageTag);
            }
            
            // Initialize d[s-1] = c[s-1] (since d[s] = 0)
            int lastBit = numBits - 1;
            Share cLast = shareStorage.getShare(protocolId + "-c-" + lastBit);
            String dLastId = protocolId + "-d-" + lastBit;
            shareStorage.storeShare(dLastId, cLast, storageTag);
            
            // e[s-1] = d[s-1] - d[s] = d[s-1] - 0 = d[s-1]
            Share eLast = cLast;
            
            // Initialize e = e[s-1]
            String eId = protocolId + "-e";
            shareStorage.storeShare(eId, eLast, storageTag);
            
            // Initialize ea = a[s-1] * e[s-1]
            int aLastBit = (int) ((knownValue >> lastBit) & 1);
            Share eaShare;
            if (aLastBit == 1) {
                eaShare = eLast;
            } else {
                // ea = 0 (create zero share with same index)
                eaShare = new Share(0, agentId, 0);
            }
            String eaId = protocolId + "-ea";
            shareStorage.storeShare(eaId, eaShare, storageTag);
            
            debug("Phase 1: Initialized - sending ack");
            
            // Send ack
            KnownCompareAckMessage ack = new KnownCompareAckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
        }
        
        /**
         * Phase 2: After multiply completes, compute d[i], e[i], accumulate e, ea
         */
        private void handleBitPhaseMessage(BitPhaseMessage msg, int senderId) {
            int bitIdx = msg.getBitIndex();
            String productId = msg.getProductSecretId();
            long knownValue = msg.getKnownValue();
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            debug("Phase 2: Processing bit " + bitIdx);
            
            // Get shares
            Share dPrev = shareStorage.getShare(protocolId + "-d-" + (bitIdx + 1));
            Share c = shareStorage.getShare(protocolId + "-c-" + bitIdx);
            Share product = shareStorage.getShare(productId);
            
            if (dPrev == null || c == null || product == null) {
                debug("ERROR: Missing shares for bit " + bitIdx);
                return;
            }
            
            // d[i] = d[i+1] + c[i] - product
            Share dCurrent = dPrev.modAdd(c, msgPrime).modSub(product, msgPrime);
            String dId = protocolId + "-d-" + bitIdx;
            shareStorage.storeShare(dId, dCurrent, storageTag);
            
            // e[i] = d[i] - d[i+1]
            Share eI = dCurrent.modSub(dPrev, msgPrime);
            
            // e = e + e[i]
            Share e = shareStorage.getShare(protocolId + "-e");
            e = e.modAdd(eI, msgPrime);
            shareStorage.storeShare(protocolId + "-e", e, storageTag);
            
            // ea = ea + a[i] * e[i]
            int aBit = (int) ((knownValue >> bitIdx) & 1);
            if (aBit == 1) {
                Share ea = shareStorage.getShare(protocolId + "-ea");
                ea = ea.modAdd(eI, msgPrime);
                shareStorage.storeShare(protocolId + "-ea", ea, storageTag);
            }
            
            debug("Phase 2: Bit " + bitIdx + " complete - sending ack");
            
            // Send ack
            BitPhaseAckMessage ack = new BitPhaseAckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
        }
        
        /**
         * Phase 3: Compute invea = 1 - ea
         */
        private void handleComputeInvEaMessage(ComputeInvEaMessage msg, int senderId) {
            String eaId = msg.getEaSecretId();
            String invEaId = msg.getInvEaSecretId();
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            debug("Phase 3: Computing invea = 1 - ea");
            
            Share ea = shareStorage.getShare(eaId);
            if (ea == null) {
                debug("ERROR: Missing ea share");
                return;
            }
            
            // invea = 1 - ea
            Share invEa = ea.oneMinus(msgPrime);
            shareStorage.storeShare(invEaId, invEa, storageTag);
            
            debug("Phase 3: invea computed - sending ack");
            
            // Send ack
            ComputeInvEaAckMessage ack = new ComputeInvEaAckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
            
            // Responder complete after this phase
            complete = true;
            successful = true;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public boolean isSuccessful() { return successful; }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public Object getResult() { return null; }
    }
    
    // =========================================================================
    // MESSAGE CLASSES
    // =========================================================================
    
    /**
     * Phase 1: Start message with known value and bit configuration.
     */
    public static class KnownCompareStartMessage implements IProtocolMessage {
        private final String protocolId;
        private final long knownValue;
        private final String bitSecretPrefix;
        private final int numBits;
        private final long prime;
        private final String storageTag;  // Tag for intermediate share storage
        private final int senderId;
        
        public KnownCompareStartMessage(String protocolId, long knownValue, 
                                        String bitSecretPrefix, int numBits,
                                        long prime, String storageTag, int senderId) {
            this.protocolId = protocolId;
            this.knownValue = knownValue;
            this.bitSecretPrefix = bitSecretPrefix;
            this.numBits = numBits;
            this.prime = prime;
            this.storageTag = storageTag;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public long getKnownValue() { return knownValue; }
        public String getBitSecretPrefix() { return bitSecretPrefix; }
        public int getNumBits() { return numBits; }
        public long getPrime() { return prime; }
        public String getStorageTag() { return storageTag; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("knownValue", knownValue);
            params.put("bitSecretPrefix", bitSecretPrefix);
            params.put("numBits", numBits);
            params.put("prime", prime);
            params.put("storageTag", storageTag);
            return params;
        }
    }
    
    /**
     * Phase 1: Acknowledgment.
     */
    public static class KnownCompareAckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public KnownCompareAckMessage(String protocolId, int senderId) {
            this.protocolId = protocolId;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        @Override
        public boolean isCompletionMessage() { return true; }
    }
    
    /**
     * Phase 2: Bit phase message after multiply completes.
     */
    public static class BitPhaseMessage implements IProtocolMessage {
        private final String protocolId;
        private final int bitIndex;
        private final String productSecretId;
        private final long knownValue;
        private final long prime;
        private final int senderId;
        
        public BitPhaseMessage(String protocolId, int bitIndex, String productSecretId,
                              long knownValue, long prime, int senderId) {
            this.protocolId = protocolId;
            this.bitIndex = bitIndex;
            this.productSecretId = productSecretId;
            this.knownValue = knownValue;
            this.prime = prime;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public int getBitIndex() { return bitIndex; }
        public String getProductSecretId() { return productSecretId; }
        public long getKnownValue() { return knownValue; }
        public long getPrime() { return prime; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("bitIndex", bitIndex);
            params.put("productSecretId", productSecretId);
            params.put("knownValue", knownValue);
            params.put("prime", prime);
            return params;
        }
    }
    
    /**
     * Phase 2: Bit phase acknowledgment.
     */
    public static class BitPhaseAckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public BitPhaseAckMessage(String protocolId, int senderId) {
            this.protocolId = protocolId;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        @Override
        public boolean isCompletionMessage() { return true; }
    }
    
    /**
     * Phase 3: Compute invea message.
     */
    public static class ComputeInvEaMessage implements IProtocolMessage {
        private final String protocolId;
        private final String eaSecretId;
        private final String invEaSecretId;
        private final long prime;
        private final int senderId;
        
        public ComputeInvEaMessage(String protocolId, String eaSecretId, 
                                   String invEaSecretId, long prime, int senderId) {
            this.protocolId = protocolId;
            this.eaSecretId = eaSecretId;
            this.invEaSecretId = invEaSecretId;
            this.prime = prime;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public String getEaSecretId() { return eaSecretId; }
        public String getInvEaSecretId() { return invEaSecretId; }
        public long getPrime() { return prime; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("eaSecretId", eaSecretId);
            params.put("invEaSecretId", invEaSecretId);
            params.put("prime", prime);
            return params;
        }
    }
    
    /**
     * Phase 3: Compute invea acknowledgment.
     */
    public static class ComputeInvEaAckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public ComputeInvEaAckMessage(String protocolId, int senderId) {
            this.protocolId = protocolId;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        @Override
        public boolean isCompletionMessage() { return true; }
    }
}

