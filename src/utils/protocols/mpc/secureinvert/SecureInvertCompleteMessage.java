package utils.protocols.mpc.secureinvert;

import utils.protocols.core.IProtocolMessage;


/**
 * Completion message for secure invert protocol.
 * 
 * Sent by participants to initiator after computing and storing their inverted share.
 */
public class SecureInvertCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    
    public SecureInvertCompleteMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureInvertProtocol.PROTOCOL_TYPE;
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
        return "SecureInvertComplete{protocolId=" + protocolId + "}";
    }
}

