package dcop.common.configuration;

import dcop.common.DCOPProblem;

/**
 * Interface for DCOP simulation configurations.
 * 
 * Defines common attributes and methods that all simulation configurations must provide.
 * This allows TestResults and other components to work with any configuration type
 * (random network, grid network, scale-free network, etc.) without tight coupling.
 * 
 * Each configuration is responsible for generating DCOP problems with its specific
 * network topology (e.g., random, grid, scale-free).
 */
public interface ISimulationConfiguration {
    
    /**
     * Gets the number of agents in the simulation.
     * 
     * @return Number of agents (N)
     */
    int getNumAgents();
    
    /**
     * Gets the domain size for agent values.
     * 
     * @return Domain size (M) - agents select values from [0, M-1]
     */
    int getDomainSize();
    
    /**
     * Gets the minimum cost value in constraint cost matrices.
     * 
     * @return Minimum cost value
     */
    int getMinCost();
    
    /**
     * Gets the maximum cost value in constraint cost matrices.
     * 
     * @return Maximum cost value
     */
    int getMaxCost();
    
    /**
     * Generates a DCOP problem instance with the configured network topology.
     * 
     * This method is responsible for creating the constraint network structure
     * (which agents are connected) and generating cost matrices for connected pairs.
     * Different implementations will create different network topologies.
     * 
     * @param seed Random seed for reproducible problem generation
     * @return A new DCOPProblem instance with the specified topology
     */
    DCOPProblem generateProblem(long seed);
    
    /**
     * Gets a human-readable string representation of the configuration.
     * Should include key parameters specific to the configuration type.
     * 
     * @return String representation for display/logging
     */
    String toString();
}
