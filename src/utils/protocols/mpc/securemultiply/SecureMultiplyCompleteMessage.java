package utils.protocols.mpc.securemultiply;

import utils.protocols.core.IProtocolMessage;


/**
 * Completion message for Secure Multiplication protocol.
 * 
 * Sent by each participant to the initiator after they have computed
 * and stored their final share of the result.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureMultiplyCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    public SecureMultiplyCompleteMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.protocolType = SecureMultiplyProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
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
        return String.format("SecureMultiplyComplete[pid=%s, from=%d]",
                           protocolId, senderId);
    }
}

