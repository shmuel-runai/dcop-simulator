package utils.protocols.mpc.copy;

import utils.protocols.core.*;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.*;

/**
 * SecureCopyShare Protocol
 * 
 * Copies a secret-shared value from one ID to another across all participants.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Protocol flow:
 * 1. Initiator broadcasts CopyShareMessage to all participants
 * 2. Each responder copies their local share from srcSecretId to dstSecretId
 * 3. Each responder sends CopyShareAckMessage back to initiator
 * 4. Initiator waits for all ACKs, then notifies listener
 */
public class SecureCopyShareProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "SECURE_COPY_SHARE";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Copy Share protocol.
     */
    public static String start(DistributedProtocolManager manager,
                               String srcSecretId, String dstSecretId,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               ISecureCopyShareListener listener) {
        SecureCopyShareProtocol protocol = new SecureCopyShareProtocol(
            srcSecretId, dstSecretId, shareStorage, listener
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
    private final String srcSecretId;
    private final String dstSecretId;
    private final IShareStorage shareStorage;
    private final ISecureCopyShareListener listener;
    
    // Embedded responder for self-message handling
    private Responder selfResponder;
    
    // Completion tracking
    private Set<Integer> receivedAcks;
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    /**
     * Creates a new SecureCopyShareProtocol as initiator.
     * 
     * @param srcSecretId Source secret ID to copy from
     * @param dstSecretId Destination secret ID to copy to
     * @param shareStorage Storage for shares
     * @param listener Callback for completion notification (can be null)
     */
    public SecureCopyShareProtocol(
            String srcSecretId,
            String dstSecretId,
            IShareStorage shareStorage,
            ISecureCopyShareListener listener) {
        this.srcSecretId = srcSecretId;
        this.dstSecretId = dstSecretId;
        this.shareStorage = shareStorage;
        this.listener = listener;
        this.receivedAcks = new HashSet<>();
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
        responderParams.put("transport", transport);  // Give transport so ACK flows back uniformly
        this.selfResponder.initialize(responderParams);
        
        // Broadcast request to all participants (including self - uniform flow)
        broadcastRequest();
    }
    
    /**
     * Broadcasts the CopyShareMessage to all participants.
     */
    private void broadcastRequest() {
        if (participants == null || participants.isEmpty()) {
            throw new ProtocolException("Cannot broadcast request: participants list is null or empty");
        }
        
        CopyShareMessage request = new CopyShareMessage(protocolId, agentId, srcSecretId, dstSecretId);
        transport.multicast(request, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof CopyShareMessage) {
            // Self-message: route to embedded responder (uniform flow)
            // Responder will send ACK back through transport
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof CopyShareAckMessage) {
            handleAck((CopyShareAckMessage) msg, senderId);
        }
    }
    
    /**
     * Handles ACK message from a responder.
     */
    private void handleAck(CopyShareAckMessage msg, int senderId) {
        receivedAcks.add(senderId);
        checkCompletion();
    }
    
    /**
     * Checks if all participants have completed.
     */
    private void checkCompletion() {
        if (participants == null || receivedAcks.size() < participants.size()) {
            return;
        }
        
        complete = true;
        successful = true;
        
        // Notify listener
        if (listener != null) {
            listener.onSecureCopyShareComplete(protocolId, srcSecretId, dstSecretId);
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
        return dstSecretId;
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
         * Initialize from params (factory-created or embedded responder).
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
            if (msg instanceof CopyShareMessage) {
                handleCopyRequestMsg((CopyShareMessage) msg);
            }
        }
        
        /**
         * Handles the CopyShareMessage and performs the copy.
         */
        private void handleCopyRequestMsg(CopyShareMessage msg) {
            if (complete) {
                return;
            }
            copyAndRespond(msg);
        }
        
        /**
         * Copies the share and sends ACK.
         * Preserves the storage attributes (tag/sticky) of the source share.
         */
        private void copyAndRespond(CopyShareMessage msg) {
            try {
                // Extract from message
                String srcSecretId = msg.getSrcSecretId();
                String dstSecretId = msg.getDstSecretId();
                int initiatorId = msg.getSenderId();
                
                // Copy local share
                Share srcShare = shareStorage.getShare(srcSecretId);
                if (srcShare == null) {
                    throw new ProtocolException("Cannot copy: source share not found: " + srcSecretId);
                }
                
                // Preserve storage attributes (tag/sticky) from source
                IShareStorage.ShareInfo srcInfo = shareStorage.getShareInfo(srcSecretId);
                if (srcInfo != null && !srcInfo.isSticky() && srcInfo.getTag() != null) {
                    shareStorage.storeShare(dstSecretId, srcShare, srcInfo.getTag());
                } else {
                    shareStorage.storeStickyShare(dstSecretId, srcShare);
                }
                
                // Send ACK to initiator
                if (transport != null) {
                    CopyShareAckMessage ack = new CopyShareAckMessage(protocolId, agentId);
                    transport.sendMessage(ack, initiatorId);
                }
                
                complete = true;
                successful = true;
                
            } catch (Exception e) {
                complete = true;
                successful = false;
                throw new ProtocolException("Failed to copy: " + e.getMessage(), e);
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
