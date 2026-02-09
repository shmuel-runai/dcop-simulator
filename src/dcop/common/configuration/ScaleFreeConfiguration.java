package dcop.common.configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dcop.common.DCOPProblem;

/**
 * Configuration parameters for DCOP simulation with scale-free network topology.
 * 
 * Uses the Barabási-Albert preferential attachment model to generate networks
 * with power-law degree distribution. This creates "hub" nodes with many connections
 * and many nodes with few connections, mimicking real-world networks.
 * 
 * Algorithm:
 * 1. Create initial clique of size initClique (fully connected)
 * 2. For each remaining node, add 'addition' edges using preferential attachment
 *    (nodes with higher degree are more likely to receive new connections)
 */
public class ScaleFreeConfiguration implements ISimulationConfiguration {
    
    // Problem generation parameters
    public int numAgents;
    public int domainSize;
    public int minCost;
    public int maxCost;
    
    // Scale-free network topology parameters
    public int initClique;   // Size of initial fully-connected cluster
    public int addition;     // Number of edges each new node adds
    
    /**
     * Creates a scale-free configuration with default values.
     */
    public ScaleFreeConfiguration() {
        // Default values
        this.numAgents = 10;
        this.domainSize = 5;
        this.minCost = 0;
        this.maxCost = 10;
        this.initClique = 4;
        this.addition = 2;
    }
    
    /**
     * Creates a scale-free configuration with specified values.
     * 
     * @param numAgents Number of agents (N)
     * @param domainSize Domain size (M)
     * @param minCost Minimum cost value
     * @param maxCost Maximum cost value
     * @param initClique Size of initial clique (must be >= 2 and <= numAgents)
     * @param addition Number of edges per new node (must be >= 1 and <= initClique)
     */
    public ScaleFreeConfiguration(int numAgents, int domainSize,
                                  int minCost, int maxCost, 
                                  int initClique, int addition) {
        this.numAgents = numAgents;
        this.domainSize = domainSize;
        this.minCost = minCost;
        this.maxCost = maxCost;
        this.initClique = initClique;
        this.addition = addition;
        
        // Validate parameters
        if (initClique < 2) {
            throw new IllegalArgumentException("initClique must be at least 2");
        }
        if (initClique > numAgents) {
            throw new IllegalArgumentException("initClique cannot exceed numAgents");
        }
        if (addition < 1) {
            throw new IllegalArgumentException("addition must be at least 1");
        }
        if (addition > initClique) {
            throw new IllegalArgumentException("addition cannot exceed initClique");
        }
    }
    
    // ISimulationConfiguration interface implementation
    
    @Override
    public int getNumAgents() {
        return numAgents;
    }
    
    @Override
    public int getDomainSize() {
        return domainSize;
    }
    
    @Override
    public int getMinCost() {
        return minCost;
    }
    
    @Override
    public int getMaxCost() {
        return maxCost;
    }
    
    @Override
    public DCOPProblem generateProblem(long seed) {
        // Create empty DCOP problem
        DCOPProblem problem = new DCOPProblem(numAgents, domainSize);
        
        // Random instance for topology and cost matrix generation
        Random random = new Random(seed);
        
        // Track node degrees for preferential attachment
        int[] degrees = new int[numAgents + 1]; // 1-indexed
        
        // Node pool for preferential attachment
        // Each node appears in the pool once per edge it has
        List<Integer> nodePool = new ArrayList<>();
        
        // ========================================
        // PHASE 1: Build initial clique
        // ========================================
        for (int i = 1; i <= initClique; i++) {
            for (int j = i + 1; j <= initClique; j++) {
                // Add edge between i and j
                int[][] costMatrix = generateRandomCostMatrix(random);
                problem.addConstraint(i, j, costMatrix);
                
                // Update degrees
                degrees[i]++;
                degrees[j]++;
                
                // Add to node pool (each node added once per edge)
                nodePool.add(i);
                nodePool.add(j);
            }
        }
        
        // ========================================
        // PHASE 2: Add remaining nodes with preferential attachment
        // ========================================
        for (int newNode = initClique + 1; newNode <= numAgents; newNode++) {
            // Track which nodes we've already connected to
            Set<Integer> connectedTo = new HashSet<>();
            
            // Add 'addition' edges using preferential attachment
            for (int edgeCount = 0; edgeCount < addition; edgeCount++) {
                // Select target node from pool (preferential attachment)
                if (nodePool.isEmpty()) {
                    continue; // No nodes to connect to
                }
                
                int targetNode = -1;
                int attempts = 0;
                do {
                    targetNode = nodePool.get(random.nextInt(nodePool.size()));
                    attempts++;
                    // Prevent infinite loop if we can't find a valid target
                    if (attempts > nodePool.size() * 2) {
                        targetNode = -1;
                        break;
                    }
                } while (connectedTo.contains(targetNode) || targetNode == newNode);
                
                if (targetNode == -1 || connectedTo.contains(targetNode)) {
                    continue; // Couldn't find valid target
                }
                
                // Add edge
                int[][] costMatrix = generateRandomCostMatrix(random);
                problem.addConstraint(newNode, targetNode, costMatrix);
                
                // Update degrees
                degrees[newNode]++;
                degrees[targetNode]++;
                
                // Add both nodes to pool (once per new edge)
                nodePool.add(newNode);
                nodePool.add(targetNode);
                
                connectedTo.add(targetNode);
            }
        }
        
        return problem;
    }
    
    /**
     * Generates a random cost matrix with uniform distribution.
     * 
     * @param random Random number generator to use
     * @return M x M cost matrix with random values in [minCost, maxCost]
     */
    private int[][] generateRandomCostMatrix(Random random) {
        int[][] costMatrix = new int[domainSize][domainSize];
        for (int i = 0; i < domainSize; i++) {
            for (int j = 0; j < domainSize; j++) {
                costMatrix[i][j] = minCost + random.nextInt(maxCost - minCost + 1);
            }
        }
        return costMatrix;
    }
    
    @Override
    public String toString() {
        return String.format("ScaleFreeConfig[N=%d, M=%d, costs=[%d,%d], initClique=%d, addition=%d]",
                             numAgents, domainSize, minCost, maxCost, initClique, addition);
    }
}
