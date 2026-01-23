package utils.protocols.common.barrier;

import utils.protocols.core.IProtocolMessage;

/**
 * Message sent by an agent to signal it has reached the barrier.
 * 
 * All agents send this to all participants when they complete their work.
 */
public class BarrierSignalMessage implements IProtocolMessage {
    
    private final String protocolId;
    
    public BarrierSignalMessage(String protocolId) {
        this.protocolId = protocolId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return "BARRIER";
    }
    
    @Override
    public int getSenderId() {
        return -1;  // Set by transport layer
    }
}
