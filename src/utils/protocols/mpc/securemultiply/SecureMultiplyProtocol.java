package utils.protocols.mpc.securemultiply;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;
import utils.crypto.secretsharing.SecretReconstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Secure Multiplication Protocol using r-secret masking.
 * 
 * Computes shares of c = a * b using deterministic r-secret masking.
 * All agents (including initiator) participate in the computation.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts SecureMultiplyRequestMessage to all participants
 * 2. Each responder computes: share_c' = (share_a * share_b + share_r) mod prime
 * 3. Each responder sends SecureMultiplyShareResponseMessage back to initiator
 * 4. Initiator reconstructs clean-c' = a*b + r (using ALL n shares)
 * 5. Initiator broadcasts SecureMultiplyBroadcastMessage (clean-c') to all agents
 * 6. Each responder computes: share_c = clean-c' - share_r
 * 7. Each responder stores share_c and sends SecureMultiplyCompleteMessage
 */
public class SecureMultiplyProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "SECURE_MULTIPLY";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Multiplication protocol.
     * 
     * @param resultTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String secretAId, String secretBId, String secretCId,
                               String rSecretId, long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureMultiplyListener listener) {
        SecureMultiplyProtocol protocol = new SecureMultiplyProtocol(
            secretAId, secretBId, secretCId, rSecretId, prime, shareStorage, resultTag, listener
        );
        return manager.startProtocol(protocol, new HashMap<>(), participants);
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
    private final String rSecretId;
    private final long prime;
    private final IShareStorage shareStorage;
    private final String resultTag;  // null for sticky, non-null for tagged storage
    private final ISecureMultiplyListener listener;
    
    // Embedded responder for self-message handling
    private Responder selfResponder;
    
    // Phase tracking
    private Map<Integer, Share> receivedShares;  // Map of agentId -> share_c'
    private Set<Integer> receivedCompletions;   // Track completion messages
    
    // Completion state
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    public SecureMultiplyProtocol(
            String secretAId,
            String secretBId,
            String secretCId,
            String rSecretId,
            long prime,
            IShareStorage shareStorage,
            String resultTag,
            ISecureMultiplyListener listener) {
        this.secretAId = secretAId;
        this.secretBId = secretBId;
        this.secretCId = secretCId;
        this.rSecretId = rSecretId;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.resultTag = resultTag;
        this.listener = listener;
        this.receivedShares = new HashMap<>();
        this.receivedCompletions = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    // =========================================================================
    // INITIATOR INITIALIZATION
    // =========================================================================
    
    @Override
    public void initialize(Map<String, Object> params) {
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
        responderParams.put("transport", transport);  // Give transport so messages flow back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Broadcast request to all participants (including self)
        broadcastRequest();
    }
    
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        SecureMultiplyRequestMessage request = new SecureMultiplyRequestMessage(
            protocolId, agentId, secretAId, secretBId, secretCId, rSecretId, prime, resultTag
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof SecureMultiplyRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureMultiplyShareResponseMessage) {
            handleShareResponse((SecureMultiplyShareResponseMessage) msg, senderId);
        } else if (msg instanceof SecureMultiplyBroadcastMessage) {
            // Self-message: route to embedded responder (uniform flow)
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureMultiplyCompleteMessage) {
            handleComplete((SecureMultiplyCompleteMessage) msg, senderId);
        }
    }
    
    /**
     * Phase 2: Receives share_c' from all participants.
     * Reconstructs clean-c' = a*b + r when all shares are received.
     */
    private void handleShareResponse(SecureMultiplyShareResponseMessage msg, int senderId) {
        receivedShares.put(senderId, msg.shareCPrime);
        
        // Check if we've received all shares (need ALL n shares for degree-2t reconstruction)
        if (participants == null || receivedShares.size() != participants.size()) {
            return;  // Not all shares received yet
        }
        
        reconstructAndBroadcast();
    }
    
    /**
     * Reconstruct clean-c' from all shares and broadcast to participants.
     */
    private void reconstructAndBroadcast() {
        try {
            // Collect shares for reconstruction
            List<Share> shares = new java.util.ArrayList<>(receivedShares.values());
            
            // Reconstruct clean-c' = a*b + r
            long cleanCPrime = SecretReconstructor.reconstructSecret(shares, prime);
            
            // Broadcast clean-c' to all participants (including self)
            SecureMultiplyBroadcastMessage broadcast = new SecureMultiplyBroadcastMessage(
                protocolId, agentId, cleanCPrime
            );
            transport.multicast(broadcast, participants);
            
        } catch (Exception e) {
            complete = true;
            successful = false;
            throw new ProtocolException("Failed to reconstruct masked value: " + e.getMessage(), e);
        }
    }
    
    /**
     * Phase 4: Receives completion messages from all participants.
     */
    private void handleComplete(SecureMultiplyCompleteMessage msg, int senderId) {
        receivedCompletions.add(senderId);
        checkCompletion();
    }
    
    private void checkCompletion() {
        if (participants == null || receivedCompletions.size() < participants.size()) {
            return;  // Not all completions received yet
        }
        
        complete = true;
        successful = true;
        
        // Notify listener if present
        if (listener != null) {
            listener.onSecureMultiplyComplete(protocolId, secretCId);
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
    public Object getResult() { return secretCId; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles the per-agent computation for multiplication.
     * 
     * Handles two phases:
     * 1. SecureMultiplyRequestMessage -> compute masked share, send response
     * 2. SecureMultiplyBroadcastMessage -> compute final share, store, send completion
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        
        // State extracted from request message
        private String secretAId;
        private String secretBId;
        private String secretCId;
        private String rSecretId;
        private long prime;
        private String resultTag;
        private int initiatorId;
        
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
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof SecureMultiplyRequestMessage) {
                handleRequest((SecureMultiplyRequestMessage) msg, senderId);
            } else if (msg instanceof SecureMultiplyBroadcastMessage) {
                handleBroadcast((SecureMultiplyBroadcastMessage) msg, senderId);
            }
        }
        
        /**
         * Phase 1: Handle multiply-and-add request.
         * Computes masked share and sends response.
         */
        private void handleRequest(SecureMultiplyRequestMessage msg, int senderId) {
            // Store parameters for later use in broadcast phase
            this.initiatorId = senderId;
            this.secretAId = msg.secretAId;
            this.secretBId = msg.secretBId;
            this.secretCId = msg.secretCId;
            this.rSecretId = msg.rSecretId;
            this.prime = msg.prime;
            this.resultTag = msg.getResultTag();
            
            try {
                // Retrieve shares from storage
                Share shareA = shareStorage.getShare(secretAId);
                Share shareB = shareStorage.getShare(secretBId);
                Share shareR = shareStorage.getShare(rSecretId);
                
                if (shareA == null || shareB == null) {
                    throw new ProtocolException("Missing required shares for multiplication: " + 
                                              secretAId + " or " + secretBId);
                }
                if (shareR == null) {
                    throw new ProtocolException("Missing r-secret share for multiplication: " + rSecretId);
                }
                
                // Compute: share_c' = (share_a * share_b + share_r) mod prime
                long productValue = (shareA.getValue() * shareB.getValue()) % prime;
                if (productValue < 0) productValue += prime;
                
                long shareCPrimeValue = (productValue + shareR.getValue()) % prime;
                if (shareCPrimeValue < 0) shareCPrimeValue += prime;
                
                // Compute the secret for debugging: (a * b + r) mod prime
                long productSecret = (shareA.getSecret() * shareB.getSecret()) % prime;
                if (productSecret < 0) productSecret += prime;
                
                long shareCPrimeSecret = (productSecret + shareR.getSecret()) % prime;
                if (shareCPrimeSecret < 0) shareCPrimeSecret += prime;
                
                Share shareCPrime = new Share(shareCPrimeSecret, agentId, shareCPrimeValue);
                //Share shareCPrime = new Share(shareCPrimeSecret, agentId, shareCPrimeValue);
                
                // Send share_c' back to initiator
                SecureMultiplyShareResponseMessage response = new SecureMultiplyShareResponseMessage(
                    protocolId, agentId, shareCPrime
                );
                transport.sendMessage(response, initiatorId);
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to compute masked share: " + e.getMessage(), e);
            }
        }
        
        /**
         * Phase 3: Handle broadcast of clean-c'.
         * Computes final share, stores it, and sends completion.
         */
        private void handleBroadcast(SecureMultiplyBroadcastMessage msg, int senderId) {
            try {
                long cleanCPrime = msg.cleanCPrime;
                
                // Retrieve r-secret share (same as in phase 1)
                Share shareR = shareStorage.getShare(rSecretId);
                if (shareR == null) {
                    throw new ProtocolException("Missing r-secret share for multiplication: " + rSecretId);
                }
                
                // Compute final share: share_c = clean-c' - share_r
                long shareCValue = (cleanCPrime - shareR.getValue() + prime) % prime;
                
                // Compute the real secret: c = (a*b + r) - r = a*b
                long shareCSecret = (cleanCPrime - shareR.getSecret() + prime) % prime;
                
                Share shareC = new Share(shareCSecret, agentId, shareCValue);
                
                // Store result share with appropriate tag
                if (resultTag == null) {
                    shareStorage.storeStickyShare(secretCId, shareC);
                } else {
                    shareStorage.storeShare(secretCId, shareC, resultTag);
                }
                
                // Send completion message to initiator
                SecureMultiplyCompleteMessage completeMsg = new SecureMultiplyCompleteMessage(
                    protocolId, agentId
                );
                transport.sendMessage(completeMsg, initiatorId);
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to compute final share: " + e.getMessage(), e);
            }
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
