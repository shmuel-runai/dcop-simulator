package dcop.common;

import java.util.*;

/**
 * Represents a Distributed Constraint Optimization Problem (DCOP).
 * 
 * DCOP Definition:
 * - N agents, each numbered 1 to N (matching Sinalgo node IDs)
 * - Each agent selects a value X from domain [0, M-1]
 * - Some agents are connected to each other
 * - Each connected pair has a cost matrix (M x M) defining the cost based on their selected values
 * - Total system cost = sum of costs for all connected agent pairs
 * 
 * IMPORTANT: Agent IDs are 1-based (agents 1 to N), not 0-based.
 * 
 * Implements Iterable<Integer> to allow iterating over agent IDs.
 */
public class DCOPProblem implements Iterable<Integer> {
    private final int numAgents;
    private final int domainSize;
    
    // Adjacency list: agentID -> list of neighbor IDs (agent IDs start from 1)
    private final Map<Integer, List<Integer>> neighbors;
    
    // Cost matrices: key = AgentPair(i,j) where i < j, value = M x M cost matrix
    private final Map<AgentPair, int[][]> costMatrices;
    
    /**
     * Represents a pair of agent IDs for use as a map key.
     * Ensures consistent ordering (smaller ID first).
     */
    private static class AgentPair {
        public final int agentID1;  // Always the smaller ID (agent IDs start from 1)
        public final int agentID2;  // Always the larger ID
        
        public AgentPair(int id1, int id2) {
            if (id1 < id2) {
                this.agentID1 = id1;
                this.agentID2 = id2;
            } else {
                this.agentID1 = id2;
                this.agentID2 = id1;
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentPair that = (AgentPair) o;
            return agentID1 == that.agentID1 && agentID2 == that.agentID2;
        }
        
        @Override
        public int hashCode() {
            return 31 * agentID1 + agentID2;
        }
        
        @Override
        public String toString() {
            return "(" + agentID1 + "," + agentID2 + ")";
        }
    }
    
    /**
     * Creates an empty DCOP problem structure.
     * Use addConstraint() to add edges and cost matrices to build the constraint network.
     * 
     * @param numAgents Number of agents (N) - agent IDs will be 1 to N
     * @param domainSize Size of value domain (M)
     */
    public DCOPProblem(int numAgents, int domainSize) {
        this.numAgents = numAgents;
        this.domainSize = domainSize;
        this.neighbors = new HashMap<>();
        this.costMatrices = new HashMap<>();
        
        // Initialize neighbor lists for agents 1 to N (agent IDs start from 1)
        for (int i = 1; i <= numAgents; i++) {
            neighbors.put(i, new ArrayList<Integer>());
        }
    }
    
    /**
     * Adds a constraint (edge) between two agents with a cost matrix.
     * Creates a bidirectional connection in the constraint graph.
     * 
     * @param agentID1 First agent ID (1 to N)
     * @param agentID2 Second agent ID (1 to N)
     * @param costMatrix M x M cost matrix for this constraint
     * @throws IllegalArgumentException if agents are the same or already connected
     */
    public void addConstraint(int agentID1, int agentID2, int[][] costMatrix) {
        if (agentID1 == agentID2) {
            throw new IllegalArgumentException("Cannot add constraint between agent and itself");
        }
        
        AgentPair pair = new AgentPair(agentID1, agentID2);
        if (costMatrices.containsKey(pair)) {
            throw new IllegalArgumentException("Constraint already exists between agents " + agentID1 + " and " + agentID2);
        }
        
        // Add to neighbor lists (bidirectional)
        neighbors.get(agentID1).add(agentID2);
        neighbors.get(agentID2).add(agentID1);
        
        // Store cost matrix (defensive copy to prevent external modification)
        int[][] matrixCopy = new int[domainSize][domainSize];
        for (int i = 0; i < domainSize; i++) {
            System.arraycopy(costMatrix[i], 0, matrixCopy[i], 0, domainSize);
        }
        costMatrices.put(pair, matrixCopy);
    }
    
    /**
     * Gets the cost for a specific value assignment between two agents.
     * 
     * @param agentID1 First agent ID (1 to N)
     * @param agentID2 Second agent ID (1 to N)
     * @param value1 Value selected by agent 1 (0 to M-1)
     * @param value2 Value selected by agent 2 (0 to M-1)
     * @return Cost for this value pair, or 0 if agents are not connected
     */
    public int getCost(int agentID1, int agentID2, int value1, int value2) {
        if (agentID1 == agentID2) {
            return 0; // No self-cost
        }
        
        // Get cost matrix for this pair
        AgentPair pair = new AgentPair(agentID1, agentID2);
        int[][] costMatrix = costMatrices.get(pair);
        
        if (costMatrix == null) {
            return 0; // Not connected, zero cost
        }
        
        // Determine correct indexing based on agent order
        int v1, v2;
        if (agentID1 < agentID2) {
            v1 = value1;
            v2 = value2;
        } else {
            v1 = value2;
            v2 = value1;
        }
        
        return costMatrix[v1][v2];
    }
    
    /**
     * Gets the cost matrix for a pair of agents.
     * 
     * @param agentID1 First agent ID (1 to N)
     * @param agentID2 Second agent ID (1 to N)
     * @return M x M cost matrix for this pair, or a zero matrix if agents are not connected
     */
    public int[][] getCostMatrix(int agentID1, int agentID2) {
        if (agentID1 == agentID2) {
            // No self-cost, return zero matrix
            return createZeroMatrix();
        }
        
        AgentPair pair = new AgentPair(agentID1, agentID2);
        int[][] costMatrix = costMatrices.get(pair);
        
        if (costMatrix == null) {
            // Not connected, return zero matrix
            return createZeroMatrix();
        }
        
        // Return a copy, transposing if necessary
        // Stored matrix is indexed as [smaller agent's value][larger agent's value]
        // Return matrix indexed as [agentID1's value][agentID2's value]
        int[][] copy = new int[domainSize][domainSize];
        boolean needsTranspose = agentID1 > agentID2;
        
        for (int i = 0; i < domainSize; i++) {
            for (int j = 0; j < domainSize; j++) {
                if (needsTranspose) {
                    copy[i][j] = costMatrix[j][i];  // Transpose
                } else {
                    copy[i][j] = costMatrix[i][j];  // Direct copy
                }
            }
        }
        return copy;
    }
    
    /**
     * Creates a zero matrix of size M x M.
     * 
     * @return M x M matrix filled with zeros
     */
    private int[][] createZeroMatrix() {
        return new int[domainSize][domainSize];
    }
    
    /**
     * Calculates the total cost of the system given all agent value assignments.
     * 
     * @param agentValues Array where agentValues[agentID] = selected value for that agent.
     *                    Array size should be N+1, with index 0 unused (agent IDs start from 1).
     * @return Total system cost (sum of costs for all connected pairs)
     */
    public int getTotalCost(int[] agentValues) {
        int totalCost = 0;
        
        // Iterate through all connected pairs (avoiding double counting)
        // Agent IDs go from 1 to N
        for (int i = 1; i <= numAgents; i++) {
            for (int j : neighbors.get(i)) {
                if (i < j) { // Only count each pair once
                    int cost = getCost(i, j, agentValues[i], agentValues[j]);
                    totalCost += cost;
                }
            }
        }
        
        return totalCost;
    }
    
    /**
     * Gets the list of neighbors (connected agents) for a given agent.
     * 
     * @param agentId Agent ID (1 to N) - agent IDs start from 1
     * @return List of neighbor agent IDs (all IDs are in range 1 to N)
     */
    public List<Integer> getNeighbors(int agentId) {
        return new ArrayList<>(neighbors.get(agentId));
    }
    
    /**
     * Checks if two agents are connected (neighbors).
     * 
     * @param agentID1 First agent ID (1 to N)
     * @param agentID2 Second agent ID (1 to N)
     * @return true if agents are connected, false otherwise
     */
    public boolean isConnected(int agentID1, int agentID2) {
        if (agentID1 == agentID2) {
            return false;
        }
        return neighbors.get(agentID1).contains(agentID2);
    }
    
    /**
     * @return Number of agents in the problem
     */
    public int getNumAgents() {
        return numAgents;
    }
    
    /**
     * @return Domain size (number of possible values each agent can select)
     */
    public int getDomainSize() {
        return domainSize;
    }
    
    /**
     * @return Total number of edges (connections) in the constraint graph
     */
    public int getNumEdges() {
        return costMatrices.size();
    }
    
    /**
     * Returns an iterator over agent IDs (1 to N).
     * Enables for-each loops: for (int agentID : problem) { ... }
     */
    @Override
    public Iterator<Integer> iterator() {
        return neighbors.keySet().iterator();
    }
    
    /**
     * @return String representation of the problem structure
     */
    @Override
    public String toString() {
        return String.format("DCOPProblem[agents=%d, domain=%d, edges=%d]", 
                             numAgents, domainSize, getNumEdges());
    }
}
