package utils.protocols.mpc.securesub;

import utils.protocols.core.IProtocolMessage;

/**
 * Completion message for secure subtraction protocol.
 * 
 * Sent by participants to initiator after storing their result share.
 */
public class SecureSubCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    
    public SecureSubCompleteMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureSubProtocol.PROTOCOL_TYPE;
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
        return "SecureSubComplete{protocolId=" + protocolId + "}";
    }
}

