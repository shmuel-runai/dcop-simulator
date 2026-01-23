package utils.protocols.mpc.distribution;

import utils.protocols.core.IProtocolMessage;


/**
 * Acknowledgment message for VectorShareDistribution.
 * 
 * Sent by participants back to the initiator after storing all shares.
 */
public class VectorShareDistributionAckMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    
    public VectorShareDistributionAckMessage(String protocolId, int senderId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return VectorShareDistributionProtocol.PROTOCOL_TYPE;
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
