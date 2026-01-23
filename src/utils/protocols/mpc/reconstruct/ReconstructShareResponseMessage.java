package utils.protocols.mpc.reconstruct;

import utils.protocols.core.IProtocolMessage;

import utils.crypto.secretsharing.Share;

/**
 * Response message containing a share for secret reconstruction.
 * 
 * Sent by participants back to the initiator with their share data.
 * Contains the full Share object for reconstruction.
 * 
 * Note: The Share object includes the actual secret value (for testing/debugging).
 * In production, you may want to only send index and value.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class ReconstructShareResponseMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    /**
     * The share being sent for reconstruction.
     * Contains index, value, and original secret (for testing).
     */
    public final Share share;
    
    public ReconstructShareResponseMessage(String protocolId, int senderId, Share share) {
        this.protocolId = protocolId;
        this.protocolType = ReconstructSecretProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.share = share;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return protocolType;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public boolean isCompletionMessage() {
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("ReconstructShareResponse[pid=%s, from=%d, share=%s]",
                           protocolId, senderId, share);
    }
}

