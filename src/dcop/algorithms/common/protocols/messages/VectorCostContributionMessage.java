package dcop.algorithms.common.protocols.messages;

import utils.protocols.core.IProtocolMessage;
import utils.crypto.secretsharing.Share;

import java.util.List;

/**
 * Message for sharing cost contributions in privacy-preserving DCOP algorithms (vectorized).
 * 
 * Contains all shares for a single (sender, targetAgent) pair.
 * Each share corresponds to a column in the cost matrix.
 * Full secretID for index i: baseSecretID + "[" + i + "]"
 */
public class VectorCostContributionMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int roundNumber;
    private final int targetAgent;
    
    /**
     * Base secretID format: "Wb_<targetAgent>-r<round>"
     * Full secretID for index i: baseSecretID + "[" + i + "]"
     */
    private final String baseSecretID;
    
    /**
     * Vector of shares, one per domain value (column).
     * shares.get(i) corresponds to secretID: baseSecretID + "[" + i + "]"
     */
    private final List<Share> shares;
    
    public VectorCostContributionMessage(String protocolId, int roundNumber, 
                                         int targetAgent, String baseSecretID,
                                         List<Share> shares) {
        this.protocolId = protocolId;
        this.roundNumber = roundNumber;
        this.targetAgent = targetAgent;
        this.baseSecretID = baseSecretID;
        this.shares = shares;
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
    
    public String getBaseSecretID() {
        return baseSecretID;
    }
    
    /**
     * Returns the full secretID for the given index.
     */
    public String getSecretID(int index) {
        return baseSecretID + "[" + index + "]";
    }
    
    public List<Share> getShares() {
        return shares;
    }
    
    public Share getShare(int index) {
        return shares.get(index);
    }
    
    /**
     * Returns the number of shares (domain size).
     */
    public int size() {
        return shares.size();
    }
}
