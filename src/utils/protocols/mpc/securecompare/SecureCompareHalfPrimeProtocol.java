package utils.protocols.mpc.securecompare;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.mpc.secureknownsub.ISecureKnownSubListener;
import utils.protocols.mpc.secureknownsub.SecureKnownSubProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.*;

/**
 * Secure Compare Half Prime Protocol.
 * 
 * Compares a value to half the prime (determines if value is in upper or lower half).
 * This is used as part of the secure comparison: the sign of (a - b) tells us if a < b.
 * 
 * Algorithm:
 * 1. Multiply input by 2: [2*a] = [a] * 2 (local constant multiplication)
 * 2. Extract LSB of [2*a] using SecureLSBProtocol
 * 3. Invert result: 1 - LSB
 * 
 * Uses Initiator/Responder pattern:
 * - Initiator (this class) orchestrates the protocol
 * - Responder (inner class) handles messages on all agents
 */
public class SecureCompareHalfPrimeProtocol implements IDistributedProtocol, ISecureLSBListener, ISecureKnownSubListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_COMPARE_HALF_PRIME";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Compare Half Prime protocol.
     * 
     * @param manager The protocol manager
     * @param inputSecretId The secret to check if in upper half of prime
     * @param outputSecretId Where to store the result
     * @param rSecretId The r-key secret ID for LSB extraction
     * @param rBitPrefix The prefix for r-key bit secrets (e.g., "r-key")
     * @param prime The prime modulus
     * @param participants List of participant agent IDs
     * @param shareStorage Share storage
     * @param resultTag Optional tag for result storage
     * @param listener Completion callback
     * @return The protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               String inputSecretId, String outputSecretId,
                               String rSecretId, String rBitPrefix,
                               long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureCompareHalfPrimeListener listener) {
        SecureCompareHalfPrimeProtocol protocol = new SecureCompareHalfPrimeProtocol(
            inputSecretId, outputSecretId, rSecretId, rBitPrefix, prime, shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        SecureLSBProtocol.registerFactory(manager);
        SecureKnownSubProtocol.registerFactory(manager);
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            SecureCompareHalfPrimeProtocol::new,  // Initiator factory (not used - we use constructor injection)
            Responder::new);                       // Responder factory
    }
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println("[HalfPrime] " + msg); }
    
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
    private ISecureCompareHalfPrimeListener listener;
    
    private String inputSecretId;
    private String outputSecretId;
    private String rSecretId;      // R-key for LSB extraction
    private String rBitPrefix;     // Prefix for r-key bit secrets
    private String x2SecretId;     // The doubled value (2 * input)
    private String lsbResultId;    // Raw LSB result (before inversion)
    private String resultTag;      // Optional tag for result storage
    
    private boolean complete;
    private boolean successful;
    
    // Self-responder for uniform message flow
    private Responder selfResponder;
    private int constMultiplyAcks = 0;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /** Default constructor for factory (not typically used for initiator). */
    public SecureCompareHalfPrimeProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    /** Constructor injection for initiator. */
    public SecureCompareHalfPrimeProtocol(String inputSecretId, String outputSecretId,
                                          String rSecretId, String rBitPrefix,
                                          long prime, IShareStorage shareStorage,
                                          String resultTag, ISecureCompareHalfPrimeListener listener) {
        this();
        this.inputSecretId = inputSecretId;
        this.outputSecretId = outputSecretId;
        this.rSecretId = rSecretId;
        this.rBitPrefix = rBitPrefix;
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
        this.x2SecretId = protocolId + "-x2";
        this.lsbResultId = protocolId + "-lsbResult";
        
        // Create self-responder for uniform message flow
        createSelfResponder();
        
        // Start the protocol
        startConstMultiply();
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
        selfResponder.initialize(responderParams);
    }
    
    // =========================================================================
    // INITIATOR FLOW
    // =========================================================================
    
    /**
     * Step 1: Broadcast constant multiply message.
     * Each agent multiplies their share by 2 locally.
     */
    private void startConstMultiply() {
        debug("Starting constant multiply by 2");
        
        // Broadcast message to all agents with resultTag for cleanup
        ConstMultiplyMessage msg = new ConstMultiplyMessage(protocolId, inputSecretId, 2, x2SecretId, resultTag, prime, agentId);
        for (int pid : participants) {
            transport.sendMessage(msg, pid);
        }
    }
    
    private void onConstMultiplyComplete() {
        debug("Constant multiply complete, starting LSB extraction");
        
        // Start LSB protocol on the doubled value with resultTag for cleanup
        SecureLSBProtocol.start(
            manager,
            x2SecretId,     // Input secret (2*a)
            rSecretId,      // R-key for LSB extraction
            rBitPrefix,     // Prefix for r-key bits
            lsbResultId,    // Where to store LSB result
            prime,
            participants,
            shareStorage,
            resultTag,      // Use resultTag for cleanup
            this            // Listener
        );
    }
    
    // =========================================================================
    // LSB LISTENER
    // =========================================================================
    
    @Override
    public void onSecureLSBComplete(String lsbProtocolId, String resultSecretId) {
        debug("LSB extraction complete, starting inversion");
        startInversion();
    }
    
    /**
     * Step 3: Invert the LSB result (1 - lsbResult) to get final result
     */
    private void startInversion() {
        debug("Starting inversion: 1 - lsbResult");
        
        SecureKnownSubProtocol.start(
            manager,
            1L,             // knownValue = 1
            lsbResultId,    // secretId = lsbResult
            outputSecretId, // result = 1 - lsbResult
            true,           // knownIsLeft = true (1 - x)
            prime,
            participants,
            shareStorage,
            resultTag,
            this  // Listener
        );
    }
    
    // =========================================================================
    // KNOWN SUB LISTENER
    // =========================================================================
    
    @Override
    public void onSecureKnownSubComplete(String knownSubProtocolId, String resultId) {
        debug("Inversion complete - half-prime comparison finished");
        
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureCompareHalfPrimeComplete(protocolId, outputSecretId);
        }
    }
    
    // =========================================================================
    // MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // Route self-messages to selfResponder for uniform flow
        if (senderId == agentId && selfResponder != null && msg instanceof ConstMultiplyMessage) {
            selfResponder.handleMessage(msg, senderId);
            return;
        }
        
        // Handle acks from all agents (including self via transport)
        if (msg instanceof ConstMultiplyAckMessage) {
            handleConstMultiplyAck((ConstMultiplyAckMessage) msg, senderId);
        }
    }
    
    private void handleConstMultiplyAck(ConstMultiplyAckMessage msg, int senderId) {
        constMultiplyAcks++;
        if (constMultiplyAcks >= participants.size()) {
            onConstMultiplyComplete();
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
    public Object getResult() { return successful ? outputSecretId : null; }
    
    // =========================================================================
    // RESPONDER INNER CLASS
    // =========================================================================
    
    /**
     * Responder handles the constant multiplication message on all agents.
     */
    public static class Responder implements IDistributedProtocol {
        
        private static final boolean DEBUG = false;
        private void debug(String msg) { if (DEBUG) System.out.println("[HalfPrime.R] " + msg); }
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private long prime;
        
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
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof ConstMultiplyMessage) {
                handleConstMultiplyMessage((ConstMultiplyMessage) msg, senderId);
            }
        }
        
        private void handleConstMultiplyMessage(ConstMultiplyMessage msg, int senderId) {
            // Multiply local share by constant
            Share inputShare = shareStorage.getShare(msg.getInputSecretId());
            if (inputShare == null) {
                debug("ERROR: Missing input share " + msg.getInputSecretId());
                return;
            }
            
            // Use prime from message (responder may not have it initialized from params)
            long msgPrime = msg.getPrime();
            if (msgPrime == 0) msgPrime = prime;
            
            Share multiplied = inputShare.constMultiply(msg.getConstant(), msgPrime);
            
            // Store with tag from message (or protocolId as fallback)
            String tag = msg.getOutputTag() != null ? msg.getOutputTag() : protocolId;
            shareStorage.storeShare(msg.getOutputSecretId(), multiplied, tag);
            
            // Send ack back through transport
            ConstMultiplyAckMessage ack = new ConstMultiplyAckMessage(protocolId, agentId);
            transport.sendMessage(ack, senderId);
            
            // Responder is done after sending ack
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
     * Message to request constant multiplication.
     */
    public static class ConstMultiplyMessage implements IProtocolMessage {
        private final String protocolId;
        private final String inputSecretId;
        private final long constant;
        private final String outputSecretId;
        private final String outputTag;  // Tag for storing output share
        private final long prime;
        private final int senderId;
        
        public ConstMultiplyMessage(String protocolId, String inputSecretId, 
                                   long constant, String outputSecretId, String outputTag,
                                   long prime, int senderId) {
            this.protocolId = protocolId;
            this.inputSecretId = inputSecretId;
            this.constant = constant;
            this.outputSecretId = outputSecretId;
            this.outputTag = outputTag;
            this.prime = prime;
            this.senderId = senderId;
        }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE; }
        
        @Override
        public int getSenderId() { return senderId; }
        
        public String getInputSecretId() { return inputSecretId; }
        public long getConstant() { return constant; }
        public String getOutputSecretId() { return outputSecretId; }
        public String getOutputTag() { return outputTag; }
        public long getPrime() { return prime; }
        
        @Override
        public Map<String, Object> extractParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("inputSecretId", inputSecretId);
            params.put("outputSecretId", outputSecretId);
            params.put("outputTag", outputTag);
            params.put("prime", prime);
            return params;
        }
    }
    
    /**
     * Acknowledgment for constant multiplication.
     */
    public static class ConstMultiplyAckMessage implements IProtocolMessage {
        private final String protocolId;
        private final int senderId;
        
        public ConstMultiplyAckMessage(String protocolId, int senderId) {
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
