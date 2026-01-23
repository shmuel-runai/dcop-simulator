package utils.protocols.mpc.dotproduct;

import utils.protocols.core.*;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.*;

/**
 * SecureDotProduct Protocol - Computes dot product of two secret-shared vectors.
 * 
 * Given vectors A and B of size d, computes:
 *   result = A[0]*B[0] + A[1]*B[1] + ... + A[d-1]*B[d-1]
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator starts d parallel SecureMultiply protocols
 * 2. All agents participate in multiplications (via SecureMultiply)
 * 3. When all multiplies complete, initiator broadcasts DotProductSumMessage
 * 4. Each responder computes local sum and sends DotProductAckMessage to initiator
 * 5. When initiator receives ACKs from ALL participants, it notifies listener
 * 
 * Key insight: Shamir secret sharing is linear, so each agent can independently
 * sum their shares, and the resulting shares form a valid sharing of the sum.
 */
public class SecureDotProductProtocol implements IDistributedProtocol, ISecureMultiplyListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_DOT_PRODUCT";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Dot Product protocol.
     * 
     * @param rSecretId The pre-distributed random secret ID for SecureMultiply
     * @param storageTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String baseIdA, String baseIdB, String outputId,
                               int vectorSize, String rSecretId, long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               ISecureDotProductListener listener) {
        SecureDotProductProtocol protocol = new SecureDotProductProtocol(
            baseIdA, baseIdB, outputId, vectorSize, rSecretId, prime, shareStorage, storageTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
    }
    
    /**
     * Registers the responder factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first
        SecureMultiplyProtocol.registerFactory(manager);
        
        // Initiator uses constructor injection, so null
        manager.registerProtocolFactory(PROTOCOL_TYPE, null, Responder::new);
    }
    
    // =========================================================================
    // INITIATOR STATE
    // =========================================================================
    
    private static final boolean DEBUG = false;
    private void debug(String message) {
        if (DEBUG) System.out.println("[DotProduct] " + message);
    }
    
    // Protocol identity
    private String protocolId;
    private int agentId;
    
    // Infrastructure (set during initialize)
    private IMessageTransport transport;
    private DistributedProtocolManager manager;
    private List<Integer> participants;
    
    // Domain data (set via constructor - proper injection)
    private final String baseIdA;
    private final String baseIdB;
    private final String outputId;
    private final int vectorSize;
    private final String rSecretId;
    private final long prime;
    private final IShareStorage shareStorage;
    private final String storageTag;
    private final ISecureDotProductListener listener;
    
    // Embedded responder for self-message handling
    private Responder selfResponder;
    
    // Completion tracking
    private Set<String> completedMultiplies;
    private Set<Integer> receivedAcks;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    public SecureDotProductProtocol(
            String baseIdA, String baseIdB, String outputId,
            int vectorSize, String rSecretId, long prime,
            IShareStorage shareStorage, String storageTag,
            ISecureDotProductListener listener) {
        this.baseIdA = baseIdA;
        this.baseIdB = baseIdB;
        this.outputId = outputId;
        this.vectorSize = vectorSize;
        this.rSecretId = rSecretId;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.storageTag = storageTag;
        this.listener = listener;
        this.completedMultiplies = new HashSet<>();
        this.receivedAcks = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    // =========================================================================
    // INITIATOR INITIALIZATION
    // =========================================================================
    
    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.transport = (IMessageTransport) params.get("transport");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.participants = (List<Integer>) params.get("participants");
        
        // Create embedded responder for self-message handling (uniform flow)
        this.selfResponder = new Responder();
        Map<String, Object> responderParams = new HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("storageTag", storageTag);
        responderParams.put("transport", transport);
        this.selfResponder.initialize(responderParams);
        
        if (vectorSize <= 0) {
            throw new IllegalArgumentException("vectorSize must be > 0, got: " + vectorSize);
        }
        
        debug("Agent " + agentId + " initializing DotProduct: " + baseIdA + " · " + baseIdB + 
              " (size=" + vectorSize + ") -> " + outputId);
        
        // Start the parallel multiplications
        startParallelMultiplies();
    }
    
    /**
     * Starts d parallel SecureMultiply protocols, one for each vector element.
     */
    private void startParallelMultiplies() {
        for (int i = 0; i < vectorSize; i++) {
            String aId = baseIdA + "[" + i + "]";
            String bId = baseIdB + "[" + i + "]";
            String productId = protocolId + "-prod[" + i + "]";
            
            debug("Agent " + agentId + " starting multiply: " + aId + " * " + bId + " -> " + productId);
            
            SecureMultiplyProtocol.start(
                manager, aId, bId, productId,
                rSecretId, prime, participants, shareStorage, storageTag, this
            );
        }
        
        debug("Agent " + agentId + " started " + vectorSize + " parallel multiplies");
    }
    
    // =========================================================================
    // MULTIPLY COMPLETION CALLBACK
    // =========================================================================
    
    @Override
    public void onSecureMultiplyComplete(String multiplyProtocolId, String resultSecretId) {
        completedMultiplies.add(resultSecretId);
        
        debug("Agent " + agentId + " multiply complete: " + resultSecretId + 
              " (" + completedMultiplies.size() + "/" + vectorSize + ")");
        
        // Check if all multiplies are done
        if (completedMultiplies.size() < vectorSize) {
            return;
        }
        
        broadcastSumMessage();
    }
    
    /**
     * Broadcasts the sum message to all participants (including self).
     */
    private void broadcastSumMessage() {
        debug("Agent " + agentId + " broadcasting sum message to " + participants.size() + " participants");
        
        DotProductSumMessage msg = new DotProductSumMessage(
            protocolId, vectorSize, outputId, baseIdA, baseIdB, prime, storageTag
        );
        
        transport.multicast(msg, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof DotProductSumMessage) {
            // Self-message: route to embedded responder (uniform flow)
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof DotProductAckMessage) {
            handleAckMessage((DotProductAckMessage) msg, senderId);
        }
    }
    
    private void handleAckMessage(DotProductAckMessage msg, int senderId) {
        if (receivedAcks.contains(senderId)) {
            debug("Agent " + agentId + " ignoring duplicate ACK from " + senderId);
            return;
        }
        
        receivedAcks.add(senderId);
        
        debug("Agent " + agentId + " received ACK from " + senderId + 
              " (" + receivedAcks.size() + "/" + participants.size() + ")");
        
        checkCompletion();
    }
    
    private void checkCompletion() {
        if (participants == null || receivedAcks.size() < participants.size()) {
            return;  // Not all ACKs received yet
        }
        
        debug("Agent " + agentId + " all ACKs received - completing protocol");
        
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureDotProductComplete(protocolId, outputId);
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
    public Object getResult() { return outputId; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles DotProductSumMessage and computes local sum.
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private String storageTag;
        
        // State from message
        private int vectorSize;
        private String outputId;
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
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
            this.storageTag = (String) params.get("storageTag");
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof DotProductSumMessage) {
                if (complete) return;
                handleSumMessage((DotProductSumMessage) msg, senderId);
            }
        }
        
        private void handleSumMessage(DotProductSumMessage msg, int senderId) {
            // Extract parameters from message
            this.vectorSize = msg.getVectorSize();
            this.outputId = msg.getOutputId();
            this.prime = msg.getPrime();
            
            // Override storageTag from message if we don't have one
            if (this.storageTag == null) {
                this.storageTag = msg.getStorageTag();
            }
            
            // Compute local sum
            boolean success = computeLocalSum();
            
            if (success) {
                // Send ACK to initiator
                DotProductAckMessage ack = new DotProductAckMessage(protocolId);
                transport.sendMessage(ack, senderId);
                
                complete = true;
                successful = true;
            } else {
                complete = true;
                successful = false;
            }
        }
        
        private boolean computeLocalSum() {
            long sumValue = 0;
            long sumSecret = 0;
            int shareIndex = -1;
            
            for (int i = 0; i < vectorSize; i++) {
                String productId = protocolId + "-prod[" + i + "]";
                Share productShare = shareStorage.getShare(productId);
                
                if (productShare == null) {
                    return false;
                }
                
                sumValue = (sumValue + productShare.getValue()) % prime;
                sumSecret = (sumSecret + productShare.getSecret()) % prime;
                shareIndex = productShare.getIndex();
            }
            
            // Create and store the result share
            Share resultShare = new Share(sumSecret, shareIndex, sumValue);
            if (storageTag == null) {
                shareStorage.storeStickyShare(outputId, resultShare);
            } else {
                shareStorage.storeShare(outputId, resultShare, storageTag);
            }
            
            return true;
        }
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public boolean isSuccessful() { return successful; }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE + "_RESPONDER"; }
        
        @Override
        public Object getResult() { return null; }
    }
}
