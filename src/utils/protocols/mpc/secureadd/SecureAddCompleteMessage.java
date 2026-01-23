package utils.protocols.mpc.secureadd;

import utils.protocols.core.IProtocolMessage;

/**
 * Completion message for Secure MPC Addition protocol.
 * 
 * Sent by each agent (including initiator) after they complete their
 * local computation and store the result share.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureAddCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    public SecureAddCompleteMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.protocolType = SecureAddProtocol.PROTOCOL_TYPE;
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
        return String.format("SecureAddComplete[pid=%s, from=%d]",
                           protocolId, senderId);
    }
}

