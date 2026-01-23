package utils.protocols.mpc.findmax;

import utils.protocols.core.IProtocolMessage;


/**
 * Completion acknowledgment message for Secure FindMax protocol.
 * 
 * Sent by agents back to initiator when they've completed initialization.
 */
public class FindMaxCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    
    public FindMaxCompleteMessage(String protocolId) {
        this.protocolId = protocolId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureFindMaxProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return -1; // Will be set by transport layer
    }
    
    @Override
    public boolean isCompletionMessage() {
        return true;
    }
}
