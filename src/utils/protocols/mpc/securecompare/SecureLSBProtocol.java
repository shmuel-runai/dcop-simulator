package utils.protocols.mpc.securecompare;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.mpc.secureadd.ISecureAddListener;
import utils.protocols.mpc.secureadd.SecureAddProtocol;
import utils.protocols.mpc.reconstruct.IReconstructListener;
import utils.protocols.mpc.reconstruct.ReconstructSecretProtocol;
import utils.protocols.mpc.secureknowncompare.ISecureKnownSecretCompareListener;
import utils.protocols.mpc.secureknowncompare.SecureKnownSecretCompareProtocol;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.*;

/**
 * Secure LSB (Least Significant Bit) Extraction Protocol.
 * 
 * Extracts the least significant bit of a secret value without revealing the value itself.
 * 
 * Algorithm:
 *   1. c = x + r           (SecureAddProtocol)
 *   2. Reconstruct c       (ReconstructSecretProtocol - everyone learns c)
 *   3. d0 = c[0] XOR r[0]  (Local: if c[0]==0 then r[0], else 1-r[0])
 *   4. e = c < r           (SecureKnownSecretCompareProtocol)
 *   5. product = e * d0    (SecureMultiplyProtocol)
 *   6. result = e + d0 - 2*product  (Local XOR formula)
 * 
 * Requires pre-distributed r-key and its bits (r-key[0], r-key[1], etc.)
 * 
 * Uses Initiator/Responder pattern with inner Responder class.
 */
public class SecureLSBProtocol implements IDistributedProtocol, 
        ISecureAddListener, IReconstructListener, 
        ISecureKnownSecretCompareListener, ISecureMultiplyListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_LSB";
    private static final int NUM_BITS = 31;  // For prime 2^31 - 1
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println("[LSB] Agent " + agentId + ": " + msg); }
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure LSB extraction protocol.
     * 
     * @param manager The protocol manager
     * @param inputSecretId The secret ID to extract LSB from (x)
     * @param rSecretId The r-key secret ID (pre-shared random)
     * @param rBitPrefix The prefix for r-key bit secrets (e.g., "r-key" for "r-key[0]", "r-key[1]", etc.)
     * @param resultSecretId Where to store the result
     * @param prime The prime modulus
     * @param participants List of participant agent IDs
     * @param shareStorage Share storage
     * @param resultTag Optional tag for result storage
     * @param listener Completion callback
     * @return The protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               String inputSecretId,
                               String rSecretId,
                               String rBitPrefix,
                               String resultSecretId,
                               long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureLSBListener listener) {
        SecureLSBProtocol protocol = new SecureLSBProtocol(
            inputSecretId, rSecretId, rBitPrefix, resultSecretId, prime, 
            shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        SecureAddProtocol.registerFactory(manager);
        ReconstructSecretProtocol.registerFactory(manager);
        SecureKnownSecretCompareProtocol.registerFactory(manager);
        SecureMultiplyProtocol.registerFactory(manager);
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            SecureLSBProtocol::new,  // Initiator factory
            Responder::new);          // Responder factory
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
    private ISecureLSBListener listener;
    
    private String inputSecretId;    // x - the input secret
    private String rSecretId;        // r-key
    private String rBitPrefix;       // Prefix for r-key bits (e.g., "r-key")
    private String resultSecretId;   // Where to store result
    private String resultTag;        // Optional tag for result storage
    
    // Intermediate secret IDs
    private String cSecretId;        // c = x + r
    private String d0SecretId;       // d0 = c[0] XOR r[0]
    private String eSecretId;        // e = c < r
    private String productSecretId;  // e * d0
    
    // State
    private long reconstructedC;     // The reconstructed value of c
    private int d0Acks = 0;
    private int finalAcks = 0;
    
    private boolean complete;
    private boolean successful;
    
    // Self-responder for uniform message flow
    private Responder selfResponder;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /** Default constructor for factory. */
    public SecureLSBProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    /** Constructor injection for initiator. */
    public SecureLSBProtocol(String inputSecretId, String rSecretId, String rBitPrefix,
                             String resultSecretId, long prime,
                             IShareStorage shareStorage, String resultTag,
                             ISecureLSBListener listener) {
        this();
        this.inputSecretId = inputSecretId;
        this.rSecretId = rSecretId;
        this.rBitPrefix = rBitPrefix;
        this.resultSecretId = resultSecretId;
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
        this.cSecretId = protocolId + "-c";
        this.d0SecretId = protocolId + "-d0";
        this.eSecretId = protocolId + "-e";
        this.productSecretId = protocolId + "-ed0";  // e * d0
        
        // Create self-responder for uniform message flow
        createSelfResponder();
        
        // Start Phase 1: c = x + r
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
        responderParams.put("rBitPrefix", rBitPrefix);
        responderParams.put("storageTag", resultTag);  // Pass resultTag for cleanup
        selfResponder.initialize(responderParams);
    }
    
    // =========================================================================
    // PHASE 1: c = x + r
    // =========================================================================
    
    private void startPhase1() {
        debug("Phase 1: Starting c = x + r");
        
        SecureAddProtocol.start(
            manager,
            inputSecretId,   // x
            rSecretId,       // r
            cSecretId,       // result: c = x + r
            prime,
            participants,
            shareStorage,
            resultTag,       // Use resultTag for cleanup
            this             // Listener
        );
    }
    
    @Override
    public void onSecureAddComplete(String addProtocolId, String resultId) {
        debug("Phase 1 complete: c = x + r stored as " + resultId);
        startPhase2();
    }
    
    // =========================================================================
    // PHASE 2: Reconstruct c
    // =========================================================================
    
    private void startPhase2() {
        debug("Phase 2: Reconstructing c");
        
        ReconstructSecretProtocol.start(
            manager,
            cSecretId,
            prime,
            participants,
            shareStorage,
            this  // Listener
        );
    }
    
    @Override
    public void onReconstructComplete(String reconstructProtocolId, String secretId, long reconstructedValue) {
        debug("Phase 2 complete: c = " + reconstructedValue);
        this.reconstructedC = reconstructedValue;
        startPhase3();
    }
    
    // =========================================================================
    // PHASE 3: d0 = c[0] XOR r[0]
    // =========================================================================
    
    private void startPhase3() {
        debug("Phase 3: Computing d0 = c[0] XOR r[0]");
        
        // Broadcast reconstructed c to compute d0
        ComputeD0Message msg = new ComputeD0Message(protocolId, reconstructedC, d0SecretId, rBitPrefix, prime, agentId);
        d0Acks = 0;
        for (int pid : participants) {
            transport.sendMessage(msg, pid);
        }
    }
    
    private void onPhase3Complete() {
        debug("Phase 3 complete: d0 computed");
        startPhase4();
    }
    
    // =========================================================================
    // PHASE 4: e = c < r
    // =========================================================================
    
    private void startPhase4() {
        debug("Phase 4: Computing e = c < r using SecureKnownSecretCompareProtocol");
        
        // c is known, r is secret with bits at rBitPrefix[i]
        SecureKnownSecretCompareProtocol.start(
            manager,
            reconstructedC,  // Known value (c)
            rBitPrefix,      // Bit secret prefix for r
            NUM_BITS,        // Number of bits
            eSecretId,       // Result: e = c < r
            rSecretId,       // R-key for multiplications within the protocol
            prime,
            participants,
            shareStorage,
            resultTag,       // Use resultTag for cleanup
            this             // Listener
        );
    }
    
    @Override
    public void onSecureKnownSecretCompareComplete(String compareProtocolId, String resultId) {
        debug("Phase 4 complete: e = c < r stored as " + resultId);
        startPhase5();
    }
    
    // =========================================================================
    // PHASE 5: product = e * d0
    // =========================================================================
    
    private void startPhase5() {
        debug("Phase 5: Computing product = e * d0");
        
        SecureMultiplyProtocol.start(
            manager,
            eSecretId,       // e
            d0SecretId,      // d0
            productSecretId, // Result: e * d0
            rSecretId,       // R-key for multiplication
            prime,
            participants,
            shareStorage,
            resultTag,       // Use resultTag for cleanup
            this             // Listener
        );
    }
    
    @Override
    public void onSecureMultiplyComplete(String multiplyProtocolId, String resultId) {
        debug("Phase 5 complete: product = e * d0 stored as " + resultId);
        startPhase6();
    }
    
    // =========================================================================
    // PHASE 6: result = e + d0 - 2*product (XOR formula)
    // =========================================================================
    
    private void startPhase6() {
        debug("Phase 6: Computing result = e + d0 - 2*product");
        
        FinalComputeMessage msg = new FinalComputeMessage(
            protocolId, eSecretId, d0SecretId, productSecretId, resultSecretId, resultTag, prime, agentId
        );
        finalAcks = 0;
        for (int pid : participants) {
            transport.sendMessage(msg, pid);
        }
    }
    
    private void onPhase6Complete() {
        debug("Protocol complete! LSB stored as " + resultSecretId);
        
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureLSBComplete(protocolId, resultSecretId);
        }
    }
    
    // =========================================================================
    // MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // Route messages to selfResponder if from self
        if (senderId == agentId && selfResponder != null) {
            if (msg instanceof ComputeD0Message || msg instanceof FinalComputeMessage) {
                selfResponder.handleMessage(msg, senderId);
                return;
            }
        }
        
        // Handle acks
        if (msg instanceof ComputeD0AckMessage) {
            d0Acks++;
            if (d0Acks >= participants.size()) {
                onPhase3Complete();
            }
        } else if (msg instanceof FinalComputeAckMessage) {
            finalAcks++;
            if (finalAcks >= participants.size()) {
                onPhase6Complete();
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
     * Responder handles local computation messages on all agents.
     */
    public static class Responder implements IDistributedProtocol {
        
        private static final boolean DEBUG = false;
        private void debug(String msg) { if (DEBUG) System.out.println("[LSB.R] Agent " + agentId + ": " + msg); }
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private long prime;
        private String rBitPrefix;
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
            
            this.rBitPrefix = (String) params.get("rBitPrefix");
            
            // Storage tag for cleanup - defaults to protocolId if not provided
            this.storageTag = (String) params.get("storageTag");
            if (this.storageTag == null) {
                this.storageTag = protocolId;
            }
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof ComputeD0Message) {
                handleComputeD0Message((ComputeD0Message) msg, senderId);
            } else if (msg instanceof FinalComputeMessage) {
                handleFinalComputeMessage((FinalComputeMessage) msg, senderId);
            }
        }
        
        /**
         * Phase 3: Compute d0 = c[0] XOR r[0]
         * Since c is known: if c[0]==0 then d0=r[0], else d0=1-r[0]
         */
        private void handleComputeD0Message(ComputeD0Message msg, int senderId) {
            long cValue = msg.getReconstructedC();
            String d0Id = msg.getD0SecretId();
            String bitPrefix = msg.getRBitPrefix();
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            int cBit0 = (int) (cValue & 1);
            
            // Get r[0] share
            String r0Id = bitPrefix + "[0]";
            Share r0Share = shareStorage.getShare(r0Id);
            if (r0Share == null) {
                debug("ERROR: Missing r[0] share at " + r0Id);
                return;
            }
            
            // d0 = c[0] XOR r[0]
            Share d0Share;
            if (cBit0 == 0) {
                // d0 = r[0]
                d0Share = r0Share;
            } else {
                // d0 = 1 - r[0]
                d0Share = r0Share.oneMinus(msgPrime);
            }
            
            shareStorage.storeShare(d0Id, d0Share, storageTag);
            debug("Phase 3: d0 computed (c[0]=" + cBit0 + ")");
            
            // Send ack
            ComputeD0AckMessage ack = new ComputeD0AckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
        }
        
        /**
         * Phase 6: Compute result = e + d0 - 2*product (XOR formula)
         */
        private void handleFinalComputeMessage(FinalComputeMessage msg, int senderId) {
            String eId = msg.getESecretId();
            String d0Id = msg.getD0SecretId();
            String productId = msg.getProductSecretId();
            String resultId = msg.getResultSecretId();
            String tag = msg.getResultTag();
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            Share eShare = shareStorage.getShare(eId);
            Share d0Share = shareStorage.getShare(d0Id);
            Share productShare = shareStorage.getShare(productId);
            
            if (eShare == null || d0Share == null || productShare == null) {
                debug("ERROR: Missing shares for final computation");
                return;
            }
            
            // result = e + d0 - 2*product
            // This is the XOR formula: a XOR b = a + b - 2*a*b
            Share twoProduct = productShare.constMultiply(2, msgPrime);
            Share result = eShare.modAdd(d0Share, msgPrime).modSub(twoProduct, msgPrime);
            
            // Store with appropriate tag
            String storeTag = (tag != null) ? tag : protocolId;
            shareStorage.storeShare(resultId, result, storeTag);
            
            debug("Phase 6: result = e + d0 - 2*product computed");
            
            // Send ack
            FinalComputeAckMessage ack = new FinalComputeAckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
            
            // Responder complete
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
     * Phase 3: Compute d0 message with reconstructed c value.
     */
    public static class ComputeD0Message implements IProtocolMessage {
        private final String protocolId;
        private final long reconstructedC;
        private final String d0SecretId;
        private final String rBitPrefix;
        private final long prime;
        private final int senderId;
        
        public ComputeD0Message(String protocolId, long reconstructedC, String d0SecretId,
                               String rBitPrefix, long prime, int senderId) {
            this.protocolId = protocolId;
            this.reconstructedC = reconstructedC;
            this.d0SecretId = d0SecretId;
            this.rBitPrefix = rBitPrefix;
            this.prime = prime;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public long getReconstructedC() { return reconstructedC; }
        public String getD0SecretId() { return d0SecretId; }
        public String getRBitPrefix() { return rBitPrefix; }
        public long getPrime() { return prime; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("reconstructedC", reconstructedC);
            params.put("d0SecretId", d0SecretId);
            params.put("rBitPrefix", rBitPrefix);
            params.put("prime", prime);
            return params;
        }
    }
    
    /**
     * Phase 3: Compute d0 acknowledgment.
     */
    public static class ComputeD0AckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public ComputeD0AckMessage(String protocolId, int senderId) {
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
     * Phase 6: Final computation message.
     */
    public static class FinalComputeMessage implements IProtocolMessage {
        private final String protocolId;
        private final String eSecretId;
        private final String d0SecretId;
        private final String productSecretId;
        private final String resultSecretId;
        private final String resultTag;
        private final long prime;
        private final int senderId;
        
        public FinalComputeMessage(String protocolId, String eSecretId, String d0SecretId,
                                  String productSecretId, String resultSecretId, String resultTag,
                                  long prime, int senderId) {
            this.protocolId = protocolId;
            this.eSecretId = eSecretId;
            this.d0SecretId = d0SecretId;
            this.productSecretId = productSecretId;
            this.resultSecretId = resultSecretId;
            this.resultTag = resultTag;
            this.prime = prime;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public String getESecretId() { return eSecretId; }
        public String getD0SecretId() { return d0SecretId; }
        public String getProductSecretId() { return productSecretId; }
        public String getResultSecretId() { return resultSecretId; }
        public String getResultTag() { return resultTag; }
        public long getPrime() { return prime; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("eSecretId", eSecretId);
            params.put("d0SecretId", d0SecretId);
            params.put("productSecretId", productSecretId);
            params.put("resultSecretId", resultSecretId);
            params.put("resultTag", resultTag);
            params.put("prime", prime);
            return params;
        }
    }
    
    /**
     * Phase 6: Final computation acknowledgment.
     */
    public static class FinalComputeAckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public FinalComputeAckMessage(String protocolId, int senderId) {
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

