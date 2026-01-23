package utils.protocols.mpc.dotproduct;

import utils.protocols.core.IProtocolMessage;


/**
 * Acknowledgment message sent by each agent after completing the local sum.
 * 
 * The initiator waits for ACKs from ALL participants before notifying
 * the listener, ensuring all agents have computed and stored their result shares.
 */
public class DotProductAckMessage implements IProtocolMessage {
    
    private final String protocolId;
    
    public DotProductAckMessage(String protocolId) {
        this.protocolId = protocolId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureDotProductProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return -1;  // Set by transport layer
    }
    
    @Override
    public boolean isCompletionMessage() {
        return true;
    }
}


