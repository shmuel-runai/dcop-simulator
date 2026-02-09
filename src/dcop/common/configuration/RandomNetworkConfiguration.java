package dcop.common.configuration;

import dcop.common.DCOPProblem;

/**
 * Configuration parameters for DCOP simulation with random network topology.
 * Contains settings needed to generate DCOP problems on random networks.
 */
public class RandomNetworkConfiguration implements ISimulationConfiguration {
    
    // Problem generation parameters
    public int numAgents;
    public int domainSize;
    public int minCost;
    public int maxCost;
    
    // Random network topology parameters
    public double networkDensity;
    
    /**
     * Creates a random network configuration with default values.
     */
    public RandomNetworkConfiguration() {
        // Default values
        this.numAgents = 10;
        this.domainSize = 5;
        this.minCost = 0;
        this.maxCost = 10;
        this.networkDensity = 0.3;
    }
    
    /**
     * Creates a random network configuration with specified values.
     * 
     * @param numAgents Number of agents (N)
     * @param domainSize Domain size (M)
     * @param minCost Minimum cost value
     * @param maxCost Maximum cost value
     * @param networkDensity Network density [0.0, 1.0]
     */
    public RandomNetworkConfiguration(int numAgents, int domainSize, 
                                       int minCost, int maxCost, 
                                       double networkDensity) {
        this.numAgents = numAgents;
        this.domainSize = domainSize;
        this.minCost = minCost;
        this.maxCost = maxCost;
        this.networkDensity = networkDensity;
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
        
        // Random instance for both topology and cost matrix generation
        java.util.Random random = new java.util.Random(seed);
        
        // Generate random network topology
        // For each pair of agents, connect them with probability = networkDensity
        for (int i = 1; i <= numAgents; i++) {
            for (int j = i + 1; j <= numAgents; j++) {
                if (random.nextDouble() < networkDensity) {
                    // Generate random cost matrix and add constraint
                    int[][] costMatrix = generateRandomCostMatrix(random);
                    problem.addConstraint(i, j, costMatrix);
                }
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
    private int[][] generateRandomCostMatrix(java.util.Random random) {
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
        return String.format("RandomNetworkConfig[N=%d, M=%d, costs=[%d,%d], density=%.2f]",
                             numAgents, domainSize, minCost, maxCost, networkDensity);
    }
}
