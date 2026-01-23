package utils.protocols.mpc.secureknownsub;

import utils.protocols.core.IProtocolMessage;


/**
 * Completion acknowledgment message for Secure Known-Value Subtraction protocol.
 */
public class SecureKnownSubCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    
    public SecureKnownSubCompleteMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureKnownSubProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public boolean isCompletionMessage() {
        return true;
    }
}
