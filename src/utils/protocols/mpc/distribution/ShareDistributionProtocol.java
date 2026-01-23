package utils.protocols.mpc.distribution;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;
import utils.crypto.secretsharing.ShareGenerator;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Share Distribution Protocol
 * 
 * Allows an initiator to share a secret value with all participants using
 * Shamir's Secret Sharing scheme.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER (receiver) side.
 * 
 * Protocol flow:
 * 1. Initiator generates shares using Shamir's scheme
 * 2. Initiator sends each participant their specific share
 * 3. Each participant receives and stores their share
 * 4. Protocol completes when all shares are distributed
 * 
 * Note: This is a one-way distribution - responders don't send back.
 */
public class ShareDistributionProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "DISTRIBUTE_SHARES";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Share Distribution protocol.
     * 
     * @param storageTag Storage scope tag (null = sticky/permanent)
     */
    public static String start(DistributedProtocolManager manager,
                               long secretValue, String secretId,
                               int threshold, long prime,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               IShareDistributionListener listener) {
        ShareDistributionProtocol protocol = new ShareDistributionProtocol(
            secretValue, secretId, threshold, prime, shareStorage, null, storageTag, listener
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
    private final long secretValue;
    private final String secretId;
    private final int threshold;
    private final long prime;
    private final IShareStorage shareStorage;
    private final IShareDistributionListener listener;
    private final Random rng;
    private final String storageTag;  // null = sticky, otherwise scoped to this tag
    
    // Embedded responder for self-message handling (uniform flow)
    private Responder selfResponder;
    
    // State
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    /**
     * Creates a new ShareDistributionProtocol as initiator.
     * 
     * @param secretValue The value to share
     * @param secretId Identifier for the secret
     * @param threshold k in Shamir's scheme
     * @param prime Prime for field arithmetic
     * @param shareStorage Storage for shares
     * @param rng Random number generator for share generation
     * @param storageTag Storage scope tag (null = sticky/permanent)
     * @param listener Callback for completion notification (can be null)
     */
    public ShareDistributionProtocol(
            long secretValue,
            String secretId,
            int threshold,
            long prime,
            IShareStorage shareStorage,
            Random rng,
            String storageTag,
            IShareDistributionListener listener) {
        this.secretValue = secretValue;
        this.secretId = secretId;
        this.threshold = threshold;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.rng = rng != null ? rng : new Random();
        this.storageTag = storageTag;
        this.listener = listener;
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
        this.selfResponder.initialize(responderParams);
        
        // Distribute shares to all participants
        distributeShares();
    }
    
    /**
     * Generates shares and distributes them to all participants.
     * 
     * Uses uniform message flow - sends to ALL participants including self.
     * Self-messages are routed back through handleMessage() -> selfResponder.
     */
    private void distributeShares() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot distribute shares: participants list is null or empty");
        }
        
        if (threshold <= 0 || threshold > participants.size()) {
            throw new ProtocolException("Invalid threshold: " + threshold + 
                                      " (must be 1 <= k <= n=" + participants.size() + ")");
        }
        
        try {
            // Generate shares using ShareGenerator
            ShareGenerator generator = new ShareGenerator(secretValue, threshold, prime, rng);
            
            // Send each participant their share (uniform flow: all including self)
            for (int i = 0; i < participants.size(); i++) {
                int participantId = participants.get(i);
                Share share = generator.generateShare(participantId);
                
                ShareDistributionMessage msg = new ShareDistributionMessage(
                    protocolId, agentId, secretId, share, threshold, prime, storageTag
                );
                
                // Uniform flow: send to ALL participants (including self)
                // Self-message will be routed back through handleMessage()
                transport.sendMessage(msg, participantId);
            }
            
            // Initiator completes immediately after sending all shares
            // Note: self-message is processed synchronously via localCallback,
            // so selfResponder has already stored our share by this point
            complete = true;
            successful = true;
            
            // Notify listener
            if (listener != null) {
                listener.onShareDistributionComplete(protocolId, secretId);
            }
            
        } catch (Exception e) {
            complete = true;
            successful = false;
            throw new ProtocolException("Failed to distribute shares: " + e.getMessage(), e);
        }
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof ShareDistributionMessage) {
            // Self-message: route to embedded responder (uniform flow)
            selfResponder.handleMessage(msg, senderId);
        }
        // No other messages expected (this is a fire-and-forget protocol)
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
        return secretId;
    }
    
    // =========================================================================
    // INNER CLASS: RESPONDER (Receiver)
    // =========================================================================
    
    /**
     * Responder (Receiver) - receives and stores the distributed share.
     * 
     * Created by factory when a non-initiator agent receives a distribution message.
     * This is a one-way receiver - it doesn't send any response back.
     */
    public static class Responder implements IDistributedProtocol {
        
        // Protocol identity
        private String protocolId;
        private int agentId;
        
        // Infrastructure
        private IShareStorage shareStorage;
        
        // State
        private boolean complete;
        private boolean successful;
        private String receivedSecretId;
        
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
            this.shareStorage = (IShareStorage) params.get("shareStorage");
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof ShareDistributionMessage) {
                handleDistributionMsg((ShareDistributionMessage) msg);
            }
        }
        
        /**
         * Handles the ShareDistributionMessage and stores the share.
         */
        private void handleDistributionMsg(ShareDistributionMessage msg) {
            if (complete) {
                return;
            }
            
            try {
                storeShareFromMessage(msg);
                
                this.receivedSecretId = msg.getSecretId();
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to store share: " + e.getMessage(), e);
            }
        }
        
        /**
         * Stores share from message into share storage.
         */
        private void storeShareFromMessage(ShareDistributionMessage msg) {
            String secretId = msg.getSecretId();
            Share share = msg.getShare();
            String tag = msg.getStorageTag();
            
            if (share == null) {
                return;  // Ignore messages without shares
            }
            
            if (tag == null) {
                shareStorage.storeStickyShare(secretId, share);
            } else {
                shareStorage.storeShare(secretId, share, tag);
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
            return receivedSecretId;
        }
    }
}
