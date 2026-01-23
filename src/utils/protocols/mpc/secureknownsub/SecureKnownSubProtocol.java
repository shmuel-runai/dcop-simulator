package utils.protocols.mpc.secureknownsub;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Secure Known-Value Subtraction Protocol.
 * 
 * Performs subtraction where one operand is a known (public) integer value
 * and the other is a shared secret. Each agent creates an on-the-fly share 
 * for the known value K: share(K) = (K, agentId, K).
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 */
public class SecureKnownSubProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "SECURE_KNOWN_SUB";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Known-Value Subtraction protocol.
     * 
     * @param resultTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               long knownValue, String secretId, String resultId,
                               boolean knownIsLeft, long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureKnownSubListener listener) {
        SecureKnownSubProtocol protocol = new SecureKnownSubProtocol(
            knownValue, secretId, resultId, knownIsLeft, prime, shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new java.util.HashMap<>(), participants);
    }
    
    /**
     * Registers the responder factory for this protocol type.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Initiator uses constructor injection (created in start()), so null
        manager.registerProtocolFactory(PROTOCOL_TYPE, null, Responder::new);
    }
    
    // =========================================================================
    // INITIATOR STATE
    // =========================================================================
    
    // Protocol identity
    private String protocolId;
    private int agentId;
    
    // Infrastructure (set during initialize)
    private IMessageTransport transport;
    private List<Integer> participants;
    
    // Domain data (set via constructor - proper injection)
    private final long knownValue;
    private final String secretId;
    private final String resultId;
    private final boolean knownIsLeft;
    private final long prime;
    private final IShareStorage shareStorage;
    private final String resultTag;  // null for sticky, non-null for tagged storage
    private final ISecureKnownSubListener listener;
    
    // Embedded responder for self-message handling
    private Responder selfResponder;
    
    // Completion tracking
    private Set<Integer> completionReceived;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    /**
     * Creates a new SecureKnownSubProtocol as initiator.
     * 
     * @param knownValue The known (public) integer value
     * @param secretId The identifier of the secret share operand
     * @param resultId The identifier for the result share
     * @param knownIsLeft If true, computes (known - secret); if false, computes (secret - known)
     * @param prime The prime for modular arithmetic
     * @param shareStorage Storage for shares
     * @param resultTag Tag for result storage (null for sticky)
     * @param listener Callback for completion notification (can be null)
     */
    public SecureKnownSubProtocol(
            long knownValue,
            String secretId,
            String resultId,
            boolean knownIsLeft,
            long prime,
            IShareStorage shareStorage,
            String resultTag,
            ISecureKnownSubListener listener) {
        this.knownValue = knownValue;
        this.secretId = secretId;
        this.resultId = resultId;
        this.knownIsLeft = knownIsLeft;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.resultTag = resultTag;
        this.listener = listener;
        this.completionReceived = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    // =========================================================================
    // INITIATOR INITIALIZATION
    // =========================================================================
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract only infrastructure params
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.transport = (IMessageTransport) params.get("transport");
        this.participants = (List<Integer>) params.get("participants");
        
        // Create embedded responder for self-message handling (uniform flow)
        this.selfResponder = new Responder();
        Map<String, Object> responderParams = new java.util.HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("transport", transport);  // Give transport so completion flows back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Broadcast request to all participants
        broadcastRequest();
    }
    
    /**
     * Broadcasts the SecureKnownSubRequest to all participants.
     */
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        SecureKnownSubRequestMessage request = new SecureKnownSubRequestMessage(
            protocolId, agentId, knownValue, secretId, resultId, knownIsLeft, prime, resultTag
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof SecureKnownSubRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send SecureKnownSubCompleteMessage back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureKnownSubCompleteMessage) {
            handleComplete((SecureKnownSubCompleteMessage) msg, senderId);
        }
    }
    
    /**
     * Handles completion message from a responder.
     */
    private void handleComplete(SecureKnownSubCompleteMessage msg, int senderId) {
        completionReceived.add(senderId);
        checkCompletion();
    }
    
    /**
     * Checks if all participants have completed.
     */
    private void checkCompletion() {
        if (participants == null || completionReceived.size() < participants.size()) {
            return;  // Not all completions received yet
        }
        
        complete = true;
        successful = true;
        
        // Notify listener
        if (listener != null) {
            listener.onSecureKnownSubComplete(protocolId, resultId);
        }
    }
    
    // =========================================================================
    // PROTOCOL INTERFACE
    // =========================================================================
    
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    @Override
    public boolean isSuccessful() {
        return successful;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }
    
    @Override
    public Object getResult() {
        return resultId;
    }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles the "other side" of the protocol.
     * 
     * Created by factory when a non-initiator agent receives a request.
     * Also embedded in initiator to handle self-computation.
     */
    public static class Responder implements IDistributedProtocol {
        
        // Protocol identity
        private String protocolId;
        private int agentId;
        
        // Infrastructure
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        
        // State
        private boolean complete;
        private boolean successful;
        
        /**
         * Default constructor for factory creation.
         */
        public Responder() {
            this.complete = false;
            this.successful = false;
        }
        
        /**
         * Initialize from params (factory-created responder).
         */
        @Override
        public void initialize(Map<String, Object> params) {
            this.protocolId = (String) params.get("protocolId");
            this.agentId = (Integer) params.get("agentId");
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof SecureKnownSubRequestMessage) {
                if (complete) return;
                computeAndRespond((SecureKnownSubRequestMessage) msg);
            }
        }
        
        /**
         * Performs the local computation and sends completion.
         */
        private void computeAndRespond(SecureKnownSubRequestMessage msg) {
            try {
                // Extract from message
                long knownValue = msg.getKnownValue();
                String secretId = msg.getSecretId();
                String resultId = msg.getResultId();
                boolean knownIsLeft = msg.isKnownIsLeft();
                long prime = msg.getPrime();
                String resultTag = msg.getResultTag();
                int initiatorId = msg.getSenderId();
                
                // Get secret share
                Share secretShare = shareStorage.getShare(secretId);
                if (secretShare == null) {
                    throw new ProtocolException("Missing secret share: " + secretId);
                }
                
                // Create on-the-fly share for known value
                Share knownShare = new Share(knownValue, agentId, knownValue);
                
                // Compute result using modSub
                Share resultShare;
                if (knownIsLeft) {
                    // result = known - secret
                    resultShare = knownShare.modSub(secretShare, prime);
                } else {
                    // result = secret - known
                    resultShare = secretShare.modSub(knownShare, prime);
                }
                if (resultTag == null) {
                    shareStorage.storeStickyShare(resultId, resultShare);
                } else {
                    shareStorage.storeShare(resultId, resultShare, resultTag);
                }
                
                // Send completion to initiator
                if (transport != null) {
                    SecureKnownSubCompleteMessage completeMsg = new SecureKnownSubCompleteMessage(protocolId, agentId);
                    transport.sendMessage(completeMsg, initiatorId);
                }
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to compute: " + e.getMessage(), e);
            }
        }
        
        @Override
        public boolean isComplete() {
            return complete;
        }
        
        @Override
        public boolean isSuccessful() {
            return successful;
        }
        
        @Override
        public String getProtocolId() {
            return protocolId;
        }
        
        @Override
        public String getProtocolType() {
            return PROTOCOL_TYPE + "_RESPONDER";
        }
        
        @Override
        public Object getResult() {
            return null;
        }
    }
}
