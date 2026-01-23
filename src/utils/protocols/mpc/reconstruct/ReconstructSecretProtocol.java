package utils.protocols.mpc.reconstruct;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;
import utils.crypto.secretsharing.SecretReconstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Secret Reconstruction Protocol.
 * 
 * Collects shares from all participants and reconstructs the secret.
 * Only the initiator learns the reconstructed value.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts ReconstructRequestMessage to all participants
 * 2. Each responder sends their share back to initiator
 * 3. Initiator collects all shares (including its own)
 * 4. Initiator reconstructs using Lagrange interpolation
 */
public class ReconstructSecretProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "RECONSTRUCT_SECRET";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secret Reconstruction protocol.
     */
    public static String start(DistributedProtocolManager manager,
                               String secretId, long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               IReconstructListener listener) {
        ReconstructSecretProtocol protocol = new ReconstructSecretProtocol(
            secretId, prime, shareStorage, listener
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
    private final String secretId;
    private final long prime;
    private final IShareStorage shareStorage;
    private final IReconstructListener listener;
    
    // Embedded responder for self-message handling (uniform flow)
    private Responder selfResponder;
    
    // Completion tracking
    private List<Share> collectedShares;
    private Long reconstructedSecret;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    /**
     * Creates a new ReconstructSecretProtocol as initiator.
     */
    public ReconstructSecretProtocol(
            String secretId,
            long prime,
            IShareStorage shareStorage,
            IReconstructListener listener) {
        this.secretId = secretId;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.listener = listener;
        this.collectedShares = new ArrayList<>();
        this.complete = false;
        this.successful = false;
        this.reconstructedSecret = null;
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
        java.util.Map<String, Object> responderParams = new java.util.HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("transport", transport);  // Give transport so response flows back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Broadcast request to ALL participants (including self - uniform flow)
        broadcastRequest();
    }
    
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        ReconstructRequestMessage request = new ReconstructRequestMessage(
            protocolId, agentId, secretId, prime
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof ReconstructRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send share back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof ReconstructShareResponseMessage) {
            handleShareResponse((ReconstructShareResponseMessage) msg, senderId);
        }
    }
    
    private void handleShareResponse(ReconstructShareResponseMessage msg, int senderId) {
        if (complete) return;
        
        collectedShares.add(msg.share);
        checkReconstruction();
    }
    
    private void checkReconstruction() {
        if (participants == null || collectedShares.size() != participants.size()) {
            return;
        }
        
        try {
            reconstructedSecret = SecretReconstructor.reconstructSecret(collectedShares, prime);
            
            complete = true;
            successful = true;
            
            if (listener != null) {
                listener.onReconstructComplete(protocolId, secretId, reconstructedSecret);
            }
            
        } catch (Exception e) {
            complete = true;
            successful = false;
            throw new ProtocolException("Failed to reconstruct secret: " + e.getMessage(), e);
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
    public Object getResult() { return reconstructedSecret; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - sends share back to initiator.
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        
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
            if (msg instanceof ReconstructRequestMessage) {
                handleReconstructRequest((ReconstructRequestMessage) msg);
            }
        }
        
        private void handleReconstructRequest(ReconstructRequestMessage msg) {
            if (complete) return;
            
            try {
                String secretId = msg.getSecretId();
                int initiatorId = msg.getSenderId();
                
                Share myShare = shareStorage.getShare(secretId);
                if (myShare == null) {
                    throw new ProtocolException("Missing share for secret: " + secretId);
                }
                
                // Send share back to initiator
                if (transport != null) {
                    ReconstructShareResponseMessage response = new ReconstructShareResponseMessage(
                        protocolId, agentId, myShare
                    );
                    transport.sendMessage(response, initiatorId);
                }
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to send share: " + e.getMessage(), e);
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
