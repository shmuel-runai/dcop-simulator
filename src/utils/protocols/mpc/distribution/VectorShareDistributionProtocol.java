package utils.protocols.mpc.distribution;

import utils.protocols.core.*;
import utils.crypto.secretsharing.BatchShareGenerator;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Vector Share Distribution Protocol.
 * 
 * Distributes a vector of secret values using Shamir's Secret Sharing.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator generates shares for all secrets using BatchShareGenerator
 * 2. Initiator sends each participant their shares via VectorShareDistributionMessage
 * 3. Each responder stores shares and sends ACK back
 * 4. Initiator completes when all ACKs received
 */
public class VectorShareDistributionProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "VECTOR_SHARE_DISTRIBUTION";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Vector Share Distribution protocol.
     * 
     * @param storageTag Storage scope tag (null = sticky/permanent)
     */
    public static String start(DistributedProtocolManager manager,
                               String baseSecretId, long[] secretValues,
                               long prime, List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               IVectorShareDistributionListener listener) {
        int threshold = participants.size() / 2;
        VectorShareDistributionProtocol protocol = new VectorShareDistributionProtocol(
            baseSecretId, secretValues, threshold, prime, shareStorage, null, storageTag, listener
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
    private final String baseSecretId;
    private final long[] secretValues;
    private final int threshold;
    private final long prime;
    private final IShareStorage shareStorage;
    private final IVectorShareDistributionListener listener;
    private final Random rng;
    private final String storageTag;  // null = sticky, otherwise scoped to this tag
    
    // Embedded responder for self-message handling (uniform flow)
    private Responder selfResponder;
    
    // Completion tracking
    private Set<Integer> acksReceived;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    /**
     * Creates a new VectorShareDistributionProtocol as initiator.
     * 
     * @param storageTag Storage scope tag (null = sticky/permanent)
     */
    public VectorShareDistributionProtocol(
            String baseSecretId,
            long[] secretValues,
            int threshold,
            long prime,
            IShareStorage shareStorage,
            Random rng,
            String storageTag,
            IVectorShareDistributionListener listener) {
        this.baseSecretId = baseSecretId;
        this.secretValues = secretValues;
        this.threshold = threshold;
        this.prime = prime;
        this.shareStorage = shareStorage;
        this.rng = rng != null ? rng : new Random();
        this.storageTag = storageTag;
        this.listener = listener;
        this.acksReceived = new HashSet<>();
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
        
        // Create embedded responder for self-handling (uniform flow)
        this.selfResponder = new Responder();
        Map<String, Object> responderParams = new java.util.HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("transport", transport);  // Give transport so ACK flows back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Distribute shares
        distributeShares();
    }
    
    /**
     * Generates and distributes shares to all participants.
     */
    private void distributeShares() {
        if (secretValues == null || secretValues.length == 0) {
            throw new ProtocolException("Initiator must provide secretValues array");
        }
        
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot distribute shares: participants list is null or empty");
        }
        
        try {
            BatchShareGenerator generator = new BatchShareGenerator(secretValues, threshold, prime, rng);
            
            for (int participantId : participants) {
                Share[] shares = generator.generateShares(participantId);
                List<Share> shareList = new ArrayList<>();
                for (Share s : shares) {
                    shareList.add(s);
                }
                
                VectorShareDistributionMessage msg = new VectorShareDistributionMessage(
                    protocolId, agentId, baseSecretId, shareList, threshold, prime, storageTag
                );
                
                transport.sendMessage(msg, participantId);
            }
            
        } catch (Exception e) {
            complete = true;
            successful = false;
            throw new ProtocolException("Failed to distribute vector shares: " + e.getMessage(), e);
        }
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof VectorShareDistributionAckMessage) {
            handleAck((VectorShareDistributionAckMessage) msg, senderId);
        } else if (msg instanceof VectorShareDistributionMessage) {
            // Self-message: route to embedded responder (uniform flow)
            handleSelfDistribution((VectorShareDistributionMessage) msg);
        }
    }
    
    /**
     * Handles self-distribution message via embedded responder.
     * Responder will send ACK back through transport, which flows to handleAck().
     */
    private void handleSelfDistribution(VectorShareDistributionMessage msg) {
        // Delegate to responder - it will send ACK back through uniform flow
        selfResponder.handleMessage(msg, agentId);
    }
    
    private void handleAck(VectorShareDistributionAckMessage msg, int senderId) {
        if (complete) return;
        
        acksReceived.add(senderId);
        checkCompletion();
    }
    
    private void checkCompletion() {
        if (participants != null && acksReceived.size() >= participants.size()) {
            complete = true;
            successful = true;
            
            if (listener != null) {
                listener.onVectorShareDistributionComplete(protocolId, baseSecretId);
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
    public Object getResult() { return baseSecretId; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - receives vector shares and sends ACK back.
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        
        private boolean complete;
        private boolean successful;
        private String receivedBaseSecretId;
        
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
            if (msg instanceof VectorShareDistributionMessage) {
                handleDistributionMsg((VectorShareDistributionMessage) msg);
            }
        }
        
        private void handleDistributionMsg(VectorShareDistributionMessage msg) {
            if (complete) return;
            
            try {
                storeSharesFromMessage(msg);
                
                this.receivedBaseSecretId = msg.getBaseSecretId();
                
                // Send ACK back
                if (transport != null) {
                    VectorShareDistributionAckMessage ack = new VectorShareDistributionAckMessage(protocolId, agentId);
                    transport.sendMessage(ack, msg.getSenderId());
                }
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to store vector shares: " + e.getMessage(), e);
            }
        }
        
        /**
         * Stores shares from message into share storage.
         */
        private void storeSharesFromMessage(VectorShareDistributionMessage msg) {
            String baseSecretId = msg.getBaseSecretId();
            List<Share> shares = msg.getShares();
            String tag = msg.getStorageTag();
            
            for (int i = 0; i < shares.size(); i++) {
                String secretId = baseSecretId + "[" + i + "]";
                if (tag == null) {
                    shareStorage.storeStickyShare(secretId, shares.get(i));
                } else {
                    shareStorage.storeShare(secretId, shares.get(i), tag);
                }
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
        public Object getResult() { return receivedBaseSecretId; }
    }
}
