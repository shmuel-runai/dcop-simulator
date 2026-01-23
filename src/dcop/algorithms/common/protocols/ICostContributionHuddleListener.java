package dcop.algorithms.common.protocols;

/**
 * Listener interface for CostContributionHuddleProtocol completion.
 * 
 * Called when all participants have accumulated their shares for the target agent
 * and sent their "ready" messages.
 */
public interface ICostContributionHuddleListener {
    
    /**
     * Called when the cost contribution huddle is complete for a target agent.
     * 
     * @param protocolId The huddle protocol ID
     * @param targetAgent The agent for whom shares were collected
     * @param roundNumber The round number
     */
    void onHuddleComplete(String protocolId, int targetAgent, int roundNumber);
}

