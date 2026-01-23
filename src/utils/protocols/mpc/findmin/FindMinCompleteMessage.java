package utils.protocols.mpc.findmin;

import utils.protocols.core.IProtocolMessage;


/**
 * Completion acknowledgment message for Secure FindMin protocol.
 * 
 * Sent by agents back to initiator when they've completed initialization.
 */
public class FindMinCompleteMessage implements IProtocolMessage {
    
    private final String protocolId;
    
    public FindMinCompleteMessage(String protocolId) {
        this.protocolId = protocolId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureFindMinProtocol.PROTOCOL_TYPE;
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

