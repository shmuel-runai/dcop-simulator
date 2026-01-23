package utils.protocols.mpc.secureadd;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;
import utils.crypto.secretsharing.FieldArithmetic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Secure MPC Addition Protocol
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts SecureAddRequestMessage to all participants
 * 2. Each responder computes: shareC = (shareA + shareB) mod prime
 * 3. Each responder sends SecureAddCompleteMessage back to initiator
 * 4. Initiator waits for all completions, then notifies listener
 * 
 * For self-messages: Initiator contains an embedded Responder that handles
 * the local computation when the initiator is also a participant.
 */
public class SecureAddProtocol implements IDistributedProtocol {
    
    /**
     * Protocol type identifier. Used for factory registration and message routing.
     */
    public static final String PROTOCOL_TYPE = "SECURE_ADD";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Addition protocol.
     * 
     * @param manager The protocol manager
     * @param secretAId First input secret ID
     * @param secretBId Second input secret ID
     * @param secretCId Output secret ID (result)
     * @param prime Prime modulus
     * @param participants List of all participant agent IDs
     * @param shareStorage Share storage for this agent
     * @param resultTag Tag for result storage (null for sticky)
     * @param listener Listener to notify when protocol completes (optional)
     * @return The unique protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               String secretAId, String secretBId, String secretCId,
                               long prime, List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureAddListener listener) {
        SecureAddProtocol protocol = new SecureAddProtocol(
            secretAId, secretBId, secretCId, prime, shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
    }
    
    /**
     * Registers the responder factory for this protocol type.
     * Call once during agent initialization.
     * 
     * @param manager The protocol manager to register with
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
    private final ISecureAddListener listener;
    
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
     * Creates a new SecureAddProtocol as initiator.
     * 
     * @param secretAId First operand share ID
     * @param secretBId Second operand share ID
     * @param secretCId Result share ID (where sum will be stored)
     * @param prime The prime for modular arithmetic
     * @param shareStorage Storage for shares
     * @param resultTag Tag for result storage (null for sticky)
     * @param listener Callback for completion notification (can be null)
     */
    public SecureAddProtocol(
            String secretAId,
            String secretBId,
            String secretCId,
            long prime,
            IShareStorage shareStorage,
            String resultTag,
            ISecureAddListener listener) {
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
        Map<String, Object> responderParams = new HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("transport", transport);  // Give transport so completion flows back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Broadcast request to all participants
        broadcastRequest();
    }
    
    /**
     * Broadcasts the SecureAddRequest to all participants.
     */
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        SecureAddRequestMessage request = new SecureAddRequestMessage(
            protocolId, agentId, secretAId, secretBId, secretCId, prime, resultTag
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof SecureAddRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send SecureAddCompleteMessage back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureAddCompleteMessage) {
            handleComplete((SecureAddCompleteMessage) msg, senderId);
        }
    }
    
    /**
     * Handles completion message from a responder.
     */
    private void handleComplete(SecureAddCompleteMessage msg, int senderId) {
        completionReceived.add(senderId);
        checkCompletion();
    }
    
    /**
     * Checks if all participants have completed.
     */
    private void checkCompletion() {
        if (participants != null && completionReceived.size() >= participants.size()) {
            complete = true;
            successful = true;
            
            // Notify listener
            if (listener != null) {
                listener.onSecureAddComplete(protocolId, secretCId);
            }
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
            if (msg instanceof SecureAddRequestMessage) {
                handleAddRequestMsg((SecureAddRequestMessage) msg);
            }
        }
        
        /**
         * Handles the SecureAddRequestMessage and computes.
         * Called either from handleMessage or directly by initiator for self-computation.
         */
        void handleAddRequestMsg(SecureAddRequestMessage msg) {
            if (complete) {
                return;
            }
            computeAndRespond(msg);
        }
        
        /**
         * Performs the local MPC addition and sends completion.
         * All parameters come from the message.
         * Uses this.transport (null for self-messages, skipping the send).
         */
        private void computeAndRespond(SecureAddRequestMessage msg) {
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
                
                // Compute: shareC = shareA + shareB (modular)
                Share shareC = shareA.modAdd(shareB, prime);
                if (resultTag == null) {
                    shareStorage.storeStickyShare(secretCId, shareC);
                } else {
                    shareStorage.storeShare(secretCId, shareC, resultTag);
                }
                
                // Send completion to initiator (transport is null for self-messages, which is fine
                // because the initiator already tracks its own completion directly)
                if (transport != null) {
                    SecureAddCompleteMessage completeMsg = new SecureAddCompleteMessage(protocolId, agentId);
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
            // Responder's result is stored in shareStorage, not here.
            // The initiator already knows the secretCId.
            return null;
        }
    }
}

