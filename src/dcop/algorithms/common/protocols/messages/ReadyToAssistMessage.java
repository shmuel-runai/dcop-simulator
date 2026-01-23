package dcop.algorithms.common.protocols.messages;

import utils.protocols.core.IProtocolMessage;

/**
 * Message indicating an agent has accumulated all cost contributions
 * for a target agent and is ready to assist.
 * 
 * Sent when an agent has received all N-1 cost contributions for a target.
 * The targetAgent field indicates which agent this ready message is for.
 */
public class ReadyToAssistMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int roundNumber;
    private final int targetAgent;  // Which agent this ready is for
    
    public ReadyToAssistMessage(String protocolId, int roundNumber, int targetAgent) {
        this.protocolId = protocolId;
        this.roundNumber = roundNumber;
        this.targetAgent = targetAgent;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return "COST_HUDDLE";
    }
    
    @Override
    public int getSenderId() {
        return -1;  // Set by transport layer
    }
    
    public int getRoundNumber() {
        return roundNumber;
    }
    
    public int getTargetAgent() {
        return targetAgent;
    }
}
