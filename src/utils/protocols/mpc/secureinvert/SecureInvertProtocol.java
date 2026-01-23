package utils.protocols.mpc.secureinvert;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Secure Invert Protocol.
 * 
 * Computes output = 1 - input on shared secrets.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts SecureInvertRequestMessage to all participants
 * 2. Each responder computes: share_output = (1 - share_input) mod prime
 * 3. Each responder sends SecureInvertCompleteMessage back to initiator
 * 4. Initiator waits for all completions, then notifies listener
 */
public class SecureInvertProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "SECURE_INVERT";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Invert protocol (computes 1 - input).
     * 
     * @param resultTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String inputSecretId, String outputSecretId,
                               long prime, List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureInvertListener listener) {
        SecureInvertProtocol protocol = new SecureInvertProtocol(
            inputSecretId, outputSecretId, prime, shareStorage, resultTag, listener
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
    private final String inputSecretId;
    private final String outputSecretId;
    private final long prime;
    private final IShareStorage shareStorage;
    private final String resultTag;  // null for sticky, non-null for tagged storage
    private final ISecureInvertListener listener;
    
    // Embedded responder for self-message handling
    private Responder selfResponder;
    
    // Completion tracking
    private Set<Integer> completionReceived;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    public SecureInvertProtocol(
            String inputSecretId,
            String outputSecretId,
            long prime,
            IShareStorage shareStorage,
            String resultTag,
            ISecureInvertListener listener) {
        this.inputSecretId = inputSecretId;
        this.outputSecretId = outputSecretId;
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
        
        // Broadcast request
        broadcastRequest();
    }
    
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        SecureInvertRequestMessage request = new SecureInvertRequestMessage(
            protocolId, inputSecretId, outputSecretId, prime, agentId, resultTag
        );
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof SecureInvertRequestMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send SecureInvertCompleteMessage back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof SecureInvertCompleteMessage) {
            handleComplete((SecureInvertCompleteMessage) msg, senderId);
        }
    }
    
    private void handleComplete(SecureInvertCompleteMessage msg, int senderId) {
        completionReceived.add(senderId);
        checkCompletion();
    }
    
    private void checkCompletion() {
        if (participants == null || completionReceived.size() < participants.size()) {
            return;  // Not all completions received yet
        }
        
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureInvertComplete(protocolId, outputSecretId);
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
    public Object getResult() { return outputSecretId; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - computes the inversion locally.
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
            if (msg instanceof SecureInvertRequestMessage) {
                if (complete) return;
                computeAndRespond((SecureInvertRequestMessage) msg);
            }
        }
        
        private void computeAndRespond(SecureInvertRequestMessage msg) {
            try {
                String inputSecretId = msg.getInputSecretId();
                String outputSecretId = msg.getOutputSecretId();
                long prime = msg.getPrime();
                String resultTag = msg.getResultTag();
                int initiatorId = msg.getSenderId();
                
                // Get input share
                Share inputShare = shareStorage.getShare(inputSecretId);
                if (inputShare == null) {
                    throw new ProtocolException("Missing required share: " + inputSecretId);
                }
                
                // Compute: output = (1 - input) mod prime
                long newValue = (1 - inputShare.getValue()) % prime;
                if (newValue < 0) newValue += prime;
                
                long realSecretOutput = (1 - inputShare.getSecret()) % prime;
                if (realSecretOutput < 0) realSecretOutput += prime;
                
                // Store result with appropriate tag
                Share outputShare = new Share(realSecretOutput, inputShare.getIndex(), newValue);
                if (resultTag == null) {
                    shareStorage.storeStickyShare(outputSecretId, outputShare);
                } else {
                    shareStorage.storeShare(outputSecretId, outputShare, resultTag);
                }
                
                // Send completion
                if (transport != null) {
                    SecureInvertCompleteMessage completeMsg = new SecureInvertCompleteMessage(protocolId, agentId);
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
