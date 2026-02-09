package dcop.algorithms.maxsum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dcop.algorithms.maxsum.sinalgo.MaxSumNode;
import dcop.common.DCOPProblem;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;

/**
 * Builds a DCOP network for vanilla (non-private) Max-Sum algorithm.
 * 
 * Creates two types of nodes:
 * 1. Agent nodes (IDs 1 to N): Hold domain values, make decisions
 * 2. Function nodes (IDs N+1 to N+F): Hold constraints, compute R values
 * 
 * Each edge in the original problem becomes a function node connecting two agents.
 * 
 * Network building order:
 * 1. Create all agent nodes FIRST (ensures IDs 1 to N)
 * 2. Create function nodes SECOND (IDs N+1 to N+F)
 * 3. Wire the network (set up transport and neighbors)
 */
public class MaxSumNetworkBuilder implements IDCOPNetworkBuilder {
    
    private final long algorithmSeed;
    private final int lastRound;
    
    // Tracking for wiring - use LinkedHashMap to preserve insertion order
    private final Map<Integer, MaxSumNode> agentNodes = new LinkedHashMap<>();
    private final Map<String, MaxSumNode> functionNodes = new LinkedHashMap<>();
    private final Map<Integer, AgentBrain> agentBrains = new LinkedHashMap<>();
    private final Map<String, FunctionBrain> functionBrains = new LinkedHashMap<>();
    
    /**
     * Creates a Max-Sum network builder.
     * 
     * @param algorithmSeed Seed for random number generation
     * @param lastRound Number of rounds to run before termination
     */
    public MaxSumNetworkBuilder(long algorithmSeed, int lastRound) {
        this.algorithmSeed = algorithmSeed;
        this.lastRound = lastRound;
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
        
        // List to hold ALL nodes (agents + functions)
        List<IDCOPAgent> allNodes = new ArrayList<>();
        
        // ========================================
        // PHASE 1: Create Agent Nodes (IDs 1 to N)
        // ========================================
        for (int agentId = 1; agentId <= numAgents; agentId++) {
            // Create brain
            AgentBrain brain = new AgentBrain(agentId, domainSize, lastRound);
            agentBrains.put(agentId, brain);
            
            // Create node
            MaxSumNode node = new MaxSumNode();
            node.setDcopIndex(agentId);
            node.setDomainSize(domainSize);
            node.setBrain(brain);
            
            agentNodes.put(agentId, node);
            allNodes.add(node);
        }
        
        // =============================================
        // PHASE 2: Create Function Nodes (IDs N+1 to N+F)
        // =============================================
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
                        domainSize, domainSize,
                        costMatrix
                    );
                    functionBrains.put(key, brain);
                    
                    // Create node
                    MaxSumNode node = new MaxSumNode();
                    node.setDcopIndex(-(agentA * 1000 + agentB));
                    node.setBrain(brain);
                    
                    functionNodes.put(key, node);
                    allNodes.add(node);
                }
            }
        }
        
        // Return network
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
            int agentId = i + 1;
            int sinalgoId = nodeIdMap.get(i);
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
            MaxSumNode node = agentNodes.get(agentId);
            
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
            MaxSumNode node = functionNodes.get(key);
            
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
    
    public MaxSumNode getAgentNode(int agentId) {
        return agentNodes.get(agentId);
    }
    
    public MaxSumNode getFunctionNode(int agentA, int agentB) {
        String key = (agentA < agentB) ? agentA + "," + agentB : agentB + "," + agentA;
        return functionNodes.get(key);
    }
    
    public Map<Integer, MaxSumNode> getAgentNodes() {
        return agentNodes;
    }
    
    public Map<String, MaxSumNode> getFunctionNodes() {
        return functionNodes;
    }
}
