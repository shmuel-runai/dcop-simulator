package utils.protocols.mpc.copy;

import utils.protocols.core.IProtocolMessage;


/**
 * Acknowledgement message sent back to initiator after copying a share locally.
 */
public class CopyShareAckMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    
    public CopyShareAckMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureCopyShareProtocol.PROTOCOL_TYPE;
    }
    
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public boolean isCompletionMessage() {
        return true;
    }
}

