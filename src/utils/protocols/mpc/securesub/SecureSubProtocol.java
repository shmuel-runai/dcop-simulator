package utils.protocols.mpc.securesub;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Secure MPC Subtraction Protocol.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts SecureSubRequestMessage to all participants
 * 2. Each responder computes: shareC = (shareA - shareB) mod prime
 * 3. Each responder sends SecureSubCompleteMessage back to initiator
 * 4. Initiator waits for all completions, then notifies listener
 * 
 * For self-messages: Initiator contains an embedded Responder that handles
 * the local computation when the initiator is also a participant.
 */
public class SecureSubProtocol implements IDistributedProtocol {
    
    /**
     * Protocol type identifier. Used for factory registration and message routing.
     */
    public static final String PROTOCOL_TYPE = "SECURE_SUB";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Subtraction protocol.
     * 
     * @param resultTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String secretAId, String secretBId, String secretCId,
                               long prime, List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureSubListener listener) {
        SecureSubProtocol protocol = new SecureSubProtocol(
            secretAId, secretBId, secretCId, prime, shareStorage, resultTag, listener
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
    private final String secretAId;
    private final String secretBId;
    private final String secretCId;
    private final long prime;
    private final IShareStorage shareStorage;
    private final String resultTag;  // null for sticky, non-null for tagged storage
    private final ISecureSubListener listener;
    
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
     * Creates a new SecureSubProtocol as initiator.
     * 
     * @param secretAId Minuend share ID
     * @param secretBId Subtrahend share ID
     * @param secretCId Result share ID (where difference will be stored)
     * @param prime The prime for modular arithmetic
     * @param shareStorage Storage for shares
     * @param resultTag Tag for result storage (null for sticky)
     * @param listener Callback for completion notification (can be null)
     */
    public SecureSubProtocol(
            String secretAId,
            String secretBId,
            String secretCId,
            long prime,
            IShareStorage shareStorage,
            String resultTag,
            ISecureSubListener listener) {
        this.secretAId = secretAId;
        this.secretBId = secretBId;
        this.secretCId = secretCId;
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
     * Broadcasts the SecureSubRequest to all participants.
     */
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        SecureSubRequestMessage request = new SecureSubRequestMessage(
            protocolId, secretAId, secretBId, secretCId, prime, agentId, resultTag
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof SecureSubRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send SecureSubCompleteMessage back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureSubCompleteMessage) {
            handleComplete((SecureSubCompleteMessage) msg, senderId);
        }
    }
    
    /**
     * Handles completion message from a responder.
     */
    private void handleComplete(SecureSubCompleteMessage msg, int senderId) {
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
            listener.onSecureSubComplete(protocolId, secretCId);
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
        return secretCId;
    }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles the "other side" of the protocol.
     * 
     * Created by factory when a non-initiator agent receives a request.
     * Also embedded in initiator to handle self-computation.
     * 
     * Minimal state - only infrastructure. All computation parameters come from the message.
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
         * Only sets up infrastructure. Computation happens in handleMessage().
         */
        @Override
        public void initialize(Map<String, Object> params) {
            // Infrastructure only
            this.protocolId = (String) params.get("protocolId");
            this.agentId = (Integer) params.get("agentId");
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
            // Computation will happen when handleMessage() is called
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof SecureSubRequestMessage) {
                if (complete) return;
                computeAndRespond((SecureSubRequestMessage) msg);
            }
        }
        
        /**
         * Performs the local MPC subtraction and sends completion.
         * All parameters come from the message.
         * Uses this.transport (null for self-messages, skipping the send).
         */
        private void computeAndRespond(SecureSubRequestMessage msg) {
            try {
                // Extract from message
                String secretAId = msg.getSecretAId();
                String secretBId = msg.getSecretBId();
                String secretCId = msg.getSecretCId();
                long prime = msg.getPrime();
                String resultTag = msg.getResultTag();
                int initiatorId = msg.getSenderId();
                
                // Get shares
                Share shareA = shareStorage.getShare(secretAId);
                Share shareB = shareStorage.getShare(secretBId);
                
                if (shareA == null || shareB == null) {
                    throw new ProtocolException("Missing required shares: " + secretAId + " or " + secretBId);
                }
                
                // Compute: shareC = shareA - shareB (modular)
                Share shareC = shareA.modSub(shareB, prime);
                if (resultTag == null) {
                    shareStorage.storeStickyShare(secretCId, shareC);
                } else {
                    shareStorage.storeShare(secretCId, shareC, resultTag);
                }
                
                // Send completion to initiator (transport is null for self-messages, which is fine
                // because the initiator already tracks its own completion directly)
                if (transport != null) {
                    SecureSubCompleteMessage completeMsg = new SecureSubCompleteMessage(protocolId, agentId);
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
            return PROTOCOL_TYPE;
        }
        
        @Override
        public Object getResult() {
            return null;
        }
    }
}
