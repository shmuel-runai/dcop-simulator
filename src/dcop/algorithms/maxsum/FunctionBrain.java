package dcop.algorithms.maxsum;

import java.math.BigInteger;

import dcop.algorithms.maxsum.messages.*;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.Global;

/**
 * Function brain for vanilla (non-private) Max-Sum algorithm.
 * 
 * Implements the function node logic without any encryption.
 * Each function node connects exactly two agents and holds their constraint matrix.
 * 
 * Algorithm:
 * 1. Receive Q from one agent
 * 2. Compute R[x] = min_y(Q[y] + cost[x][y]) for each domain value x
 * 3. Send R to the OTHER agent connected to this function
 */
public class FunctionBrain implements IMaxSumBrain {
    
    // Debug flag
    private static final boolean DEBUG = false;
    
    // Identity (function connects agentA and agentB)
    private final int agentA;
    private final int agentB;
    private final int domainSizeA;
    private final int domainSizeB;
    
    // Constraint matrix: cost[i][j] is cost when A=i and B=j
    private final int[][] constraintMatrix;
    
    // Transport (injected after construction)
    private INodeTransport transport;
    
    // Agent node IDs (Sinalgo IDs)
    private int agentANodeId;
    private int agentBNodeId;
    
    /**
     * Create a FunctionBrain for vanilla Max-Sum.
     * 
     * @param agentA First agent's DCOP ID (1-based)
     * @param agentB Second agent's DCOP ID (1-based)
     * @param domainSizeA Domain size for agent A
     * @param domainSizeB Domain size for agent B
     * @param constraintMatrix Cost matrix [domainSizeA][domainSizeB]
     */
    public FunctionBrain(int agentA, int agentB, 
                         int domainSizeA, int domainSizeB,
                         int[][] constraintMatrix) {
        this.agentA = agentA;
        this.agentB = agentB;
        this.domainSizeA = domainSizeA;
        this.domainSizeB = domainSizeB;
        this.constraintMatrix = constraintMatrix;
    }
    
    // ========== Injection setters ==========
    
    public void setTransport(INodeTransport transport) {
        this.transport = transport;
    }
    
    public void setAgentNodeIds(int agentANodeId, int agentBNodeId) {
        this.agentANodeId = agentANodeId;
        this.agentBNodeId = agentBNodeId;
    }
    
    // ========== Helper methods ==========
    
    private int getOtherAgent(int agentId) {
        return (agentId == agentA) ? agentB : agentA;
    }
    
    private int getAgentNodeId(int agentId) {
        return (agentId == agentA) ? agentANodeId : agentBNodeId;
    }
    
    private int getDomainSize(int agentId) {
        return (agentId == agentA) ? domainSizeA : domainSizeB;
    }
    
    /**
     * Get the constraint matrix oriented for the target agent.
     * When computing R for agentA, we use matrix as-is.
     * When computing R for agentB, we use the transposed view.
     */
    private int getConstraint(int targetAgent, int targetIdx, int otherIdx) {
        if (targetAgent == agentA) {
            return constraintMatrix[targetIdx][otherIdx];
        } else {
            return constraintMatrix[otherIdx][targetIdx];
        }
    }
    
    // ========== IMaxSumBrain implementation ==========
    
    @Override
    public void init() {
        // Nothing to initialize
    }
    
    @Override
    public void start() {
        // Function nodes are passive - they just respond to messages
    }
    
    @Override
    public void handleMessage(Message msg, int senderNodeId) {
        if (msg instanceof UpdateQsMessage) {
            handleUpdateQs((UpdateQsMessage) msg, senderNodeId);
        }
    }
    
    @Override
    public boolean isDone() {
        // Function nodes are always "done" - they're passive
        return true;
    }
    
    @Override
    public int getAssignment() {
        // Function nodes don't have assignments
        return -1;
    }
    
    @Override
    public int getRound() {
        return 0;  // Function nodes don't track rounds
    }
    
    @Override
    public void logState() {
        debug("State of Function(" + agentA + "," + agentB + ")");
        debug("  A domain size: " + domainSizeA);
        debug("  B domain size: " + domainSizeB);
        debug("  Constraint matrix:");
        for (int i = 0; i < domainSizeA; i++) {
            StringBuilder line = new StringBuilder("    [");
            for (int j = 0; j < domainSizeB; j++) {
                if (j > 0) line.append(", ");
                line.append(constraintMatrix[i][j]);
            }
            line.append("]");
            debug(line.toString());
        }
    }
    
    @Override
    public int getIndex() {
        // Return an encoded function identifier (negative to distinguish from agents)
        return -(agentA * 1000 + agentB);
    }
    
    @Override
    public boolean isAgent() {
        return false;
    }
    
    @Override
    public void tick() {
        // Function nodes are passive - they don't initiate messages
    }
    
    // ========== Message Handler ==========
    
    private void handleUpdateQs(UpdateQsMessage msg, int senderNodeId) {
        debug("Handling UpdateQsMessage: " + msg);
        
        // Determine which agent sent this and which is the target
        // The sender's Q tells us about the "other" agent (msg.targetId)
        // We compute R for the agent that SENT this message
        
        int senderAgentId;
        if (senderNodeId == agentANodeId) {
            senderAgentId = agentA;
        } else if (senderNodeId == agentBNodeId) {
            senderAgentId = agentB;
        } else {
            debug("ERROR: Unknown sender node ID: " + senderNodeId);
            return;
        }
        
        int targetAgentId = getOtherAgent(senderAgentId);
        int targetDomainSize = getDomainSize(targetAgentId);
        int senderDomainSize = msg.q.length;
        
        debug("Sender: " + senderAgentId + " (domain=" + senderDomainSize + ")");
        debug("Target: " + targetAgentId + " (domain=" + targetDomainSize + ")");
        
        // Compute R for each value in target's domain
        // R[x] = min_y(Q[y] + cost(x, y))
        BigInteger[] Rs = new BigInteger[targetDomainSize];
        
        for (int x = 0; x < targetDomainSize; x++) {
            Rs[x] = calcRx(x, msg.q, targetAgentId, senderDomainSize);
        }
        
        // Send R to the TARGET agent (not the sender!)
        int targetNodeId = getAgentNodeId(targetAgentId);
        UpdateRsMessage response = new UpdateRsMessage(senderAgentId, msg.round, Rs);
        debug("Sending Rs " + response.rString() + " to node " + targetNodeId);
        transport.sendMessage(response, targetNodeId);
    }
    
    /**
     * Compute R value for domain index x.
     * R[x] = min_y(Q[y] + cost[x][y])
     */
    private BigInteger calcRx(int x, BigInteger[] Qs, int targetAgent, int otherDomainSize) {
        // Start with y=0
        BigInteger minR = Qs[0].add(BigInteger.valueOf(getConstraint(targetAgent, x, 0)));
        
        for (int y = 1; y < otherDomainSize; y++) {
            BigInteger tmp = Qs[y].add(BigInteger.valueOf(getConstraint(targetAgent, x, y)));
            minR = minR.min(tmp);
        }
        
        return minR;
    }
    
    // ========== Debug helper ==========
    
    private void debug(String msg) {
        if (DEBUG) {
            Global.log.logln(true, "FunctionBrain[" + agentA + "," + agentB + "]: " + msg);
        }
    }
}
