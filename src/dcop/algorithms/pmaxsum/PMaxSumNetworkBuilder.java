package dcop.algorithms.pmaxsum;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import crypto.utils.Paillier;
import crypto.utils.PaillierMgr;
import dcop.algorithms.pmaxsum.sinalgo.PMaxSumNode;
import dcop.common.DCOPProblem;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;

/**
 * Builds a DCOP network for P-MAXSUM algorithm.
 * 
 * Creates two types of nodes:
 * 1. Agent nodes (IDs 1 to N): Hold domain values, make decisions
 * 2. Function nodes (IDs N+1 to N+F): Hold constraints, compute R values
 * 
 * Each edge in the original problem becomes a function node connecting two agents.
 * 
 * Network building order (as specified in design):
 * 1. Create all agent nodes FIRST (ensures IDs 1 to N)
 * 2. Create function nodes SECOND (IDs N+1 to N+F)
 * 3. Wire the network (set up transport and neighbors)
 */
public class PMaxSumNetworkBuilder implements IDCOPNetworkBuilder {
    
    // Prime for field arithmetic - should be large enough for computations
    // Using a known safe prime
    private static final BigInteger DEFAULT_PRIME = new BigInteger("2147483647"); // 2^31 - 1
    
    private final long algorithmSeed;
    private final int lastRound;
    private BigInteger prime;
    
    // Tracking for wiring - use LinkedHashMap to preserve insertion order
    private final Map<Integer, PMaxSumNode> agentNodes = new LinkedHashMap<>();  // agentId -> node
    private final Map<String, PMaxSumNode> functionNodes = new LinkedHashMap<>(); // "a,b" -> node
    private final Map<Integer, AgentBrain> agentBrains = new LinkedHashMap<>();   // agentId -> brain
    private final Map<String, FunctionBrain> functionBrains = new LinkedHashMap<>(); // "a,b" -> brain
    
    /**
     * Creates a P-MAXSUM network builder.
     * 
     * @param algorithmSeed Seed for random number generation
     * @param lastRound Number of rounds to run before termination
     */
    public PMaxSumNetworkBuilder(long algorithmSeed, int lastRound) {
        this.algorithmSeed = algorithmSeed;
        this.lastRound = lastRound;
        this.prime = DEFAULT_PRIME;
    }
    
    /**
     * Creates a P-MAXSUM network builder with custom prime.
     * 
     * @param algorithmSeed Seed for random number generation
     * @param lastRound Number of rounds to run
     * @param prime Prime modulus for field arithmetic
     */
    public PMaxSumNetworkBuilder(long algorithmSeed, int lastRound, BigInteger prime) {
        this.algorithmSeed = algorithmSeed;
        this.lastRound = lastRound;
        this.prime = prime;
    }
    
    @Override
    public DCOPNetwork buildNetwork(DCOPProblem problem) {
        int numAgents = problem.getNumAgents();
        int domainSize = problem.getDomainSize();
        
        // Clear tracking maps
        agentNodes.clear();
        functionNodes.clear();
        agentBrains.clear();
        functionBrains.clear();
        
        // Shared Paillier manager (all nodes share keys)
        PaillierMgr paillierMgr = new PaillierMgr();
        
        // List to hold ALL nodes (agents + functions)
        List<IDCOPAgent> allNodes = new ArrayList<>();
        
        // ========================================
        // PHASE 1: Create Agent Nodes (IDs 1 to N)
        // ========================================
        for (int agentId = 1; agentId <= numAgents; agentId++) {
            // Create brain
            AgentBrain brain = new AgentBrain(agentId, domainSize, lastRound, paillierMgr, prime);
            agentBrains.put(agentId, brain);
            
            // Create node
            PMaxSumNode node = new PMaxSumNode();
            node.setDcopIndex(agentId);
            node.setDomainSize(domainSize);
            node.setBrain(brain);
            
            agentNodes.put(agentId, node);
            allNodes.add(node);
        }
        
        // =============================================
        // PHASE 2: Create Function Nodes (IDs N+1 to N+F)
        // =============================================
        // Each edge in the problem graph becomes a function node
        for (int agentA = 1; agentA <= numAgents; agentA++) {
            for (int agentB : problem.getNeighbors(agentA)) {
                // Only create function for ordered pairs (A < B) to avoid duplicates
                if (agentA < agentB) {
                    String key = agentA + "," + agentB;
                    
                    // Get constraint matrix
                    int[][] costMatrix = problem.getCostMatrix(agentA, agentB);
                    
                    // Create brain
                    FunctionBrain brain = new FunctionBrain(
                        agentA, agentB,
                        domainSize, domainSize,  // Both agents have same domain size
                        costMatrix,
                        paillierMgr, prime
                    );
                    functionBrains.put(key, brain);
                    
                    // Create node
                    PMaxSumNode node = new PMaxSumNode();
                    // Use negative encoded ID for functions
                    node.setDcopIndex(-(agentA * 1000 + agentB));
                    node.setBrain(brain);
                    
                    functionNodes.put(key, node);
                    allNodes.add(node);
                }
            }
        }
        
        // =============================================
        // PHASE 3: Wire the Network
        // =============================================
        // Note: We can't fully wire here because Sinalgo node IDs aren't assigned yet.
        // The actual wiring happens after deployment in CustomGlobal.
        // However, we CAN set up the brain neighbor relationships using agent IDs.
        
        // For each agent, tell it about its function neighbors
        for (int agentA = 1; agentA <= numAgents; agentA++) {
            AgentBrain brain = agentBrains.get(agentA);
            
            for (int agentB : problem.getNeighbors(agentA)) {
                // The function node key is always (min, max)
                String key = (agentA < agentB) ? agentA + "," + agentB : agentB + "," + agentA;
                
                // We'll set the actual Sinalgo node IDs in a post-deployment step
                // For now, just track that this agent connects to this other agent via a function
                // The actual node IDs will be set in wireNetwork() after deployment
            }
        }
        
        // Return network - note that allNodes contains both agents and functions
        // The first N entries are agents (IDs 1-N), rest are function nodes
        return new DCOPNetwork(problem, allNodes);
    }
    
    /**
     * Wire the network after Sinalgo has assigned node IDs.
     * This MUST be called after deployNetwork() in CustomGlobal.
     * 
     * @param problem The DCOP problem
     * @param nodeIdMap Map from list index (0-based) to Sinalgo node ID
     */
    public void wireNetwork(DCOPProblem problem, Map<Integer, Integer> nodeIdMap) {
        int numAgents = problem.getNumAgents();
        
        // Build reverse map: agentId -> Sinalgo node ID
        Map<Integer, Integer> agentIdToNodeId = new HashMap<>();
        for (int i = 0; i < numAgents; i++) {
            int agentId = i + 1;  // Agent IDs are 1-based
            int sinalgoId = nodeIdMap.get(i);  // List index is 0-based
            agentIdToNodeId.put(agentId, sinalgoId);
        }
        
        // Build function key -> Sinalgo node ID map
        Map<String, Integer> functionKeyToNodeId = new HashMap<>();
        int idx = numAgents;
        for (String key : functionNodes.keySet()) {
            functionKeyToNodeId.put(key, nodeIdMap.get(idx));
            idx++;
        }
        
        // Wire agent brains to function nodes
        for (int agentId = 1; agentId <= numAgents; agentId++) {
            AgentBrain brain = agentBrains.get(agentId);
            PMaxSumNode node = agentNodes.get(agentId);
            
            // Set transport
            brain.setTransport(node);
            
            // Add function neighbors
            for (int neighborId : problem.getNeighbors(agentId)) {
                String key = (agentId < neighborId) ? agentId + "," + neighborId : neighborId + "," + agentId;
                int functionNodeId = functionKeyToNodeId.get(key);
                brain.addFunctionNeighbor(neighborId, functionNodeId);
            }
        }
        
        // Wire function brains to agent nodes
        for (String key : functionBrains.keySet()) {
            FunctionBrain brain = functionBrains.get(key);
            PMaxSumNode node = functionNodes.get(key);
            
            // Parse agent IDs from key
            String[] parts = key.split(",");
            int agentA = Integer.parseInt(parts[0]);
            int agentB = Integer.parseInt(parts[1]);
            
            // Set transport
            brain.setTransport(node);
            
            // Set agent node IDs
            int nodeIdA = agentIdToNodeId.get(agentA);
            int nodeIdB = agentIdToNodeId.get(agentB);
            brain.setAgentNodeIds(nodeIdA, nodeIdB);
        }
    }
    
    /**
     * Get the agent node for a given agent ID.
     * Available after buildNetwork() is called.
     * 
     * @param agentId The agent ID (1-based)
     * @return The PMaxSumNode, or null if not found
     */
    public PMaxSumNode getAgentNode(int agentId) {
        return agentNodes.get(agentId);
    }
    
    /**
     * Get the function node for a pair of agents.
     * Available after buildNetwork() is called.
     * 
     * @param agentA First agent ID (1-based)
     * @param agentB Second agent ID (1-based)
     * @return The PMaxSumNode, or null if not found
     */
    public PMaxSumNode getFunctionNode(int agentA, int agentB) {
        String key = (agentA < agentB) ? agentA + "," + agentB : agentB + "," + agentA;
        return functionNodes.get(key);
    }
    
    /**
     * Get all agent nodes.
     */
    public Map<Integer, PMaxSumNode> getAgentNodes() {
        return agentNodes;
    }
    
    /**
     * Get all function nodes.
     */
    public Map<String, PMaxSumNode> getFunctionNodes() {
        return functionNodes;
    }
}
