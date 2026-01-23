package dcop.common.nodes;

/**
 * Interface for DCOP agents.
 * Defines the contract that all DCOP agent implementations must fulfill.
 * This allows different algorithm implementations (Basic, MGM, DSA, etc.)
 * to work with the same network infrastructure.
 */
public interface IDCOPAgent {
    
    /**
     * Sets the domain size (number of possible values) for this agent.
     * Should be called before init().
     * 
     * @param domainSize Number of possible values (0 to M-1)
     */
    void setDomainSize(int domainSize);
    
    /**
     * Starts the algorithm execution for this agent.
     */
    void startAlgorithm();
    
    /**
     * Stops the algorithm execution, freezing current state.
     */
    void stopAlgorithm();
    
    /**
     * Gets the currently selected value.
     * 
     * @return Selected value (0 to M-1)
     */
    int getSelectedValue();
    
    /**
     * Checks if the agent is currently active.
     * 
     * @return true if algorithm is running
     */
    boolean isActive();
    
    /**
     * Gets the unique ID of this agent.
     * For Sinalgo implementations, this delegates to Node.ID.
     * 
     * @return Agent ID (1-based)
     */
    int getID();
    
    /**
     * Gets an algorithm-specific property value.
     * Supported properties vary by implementation.
     * 
     * Common properties:
     * - "rounds": Number of algorithm rounds executed (DSA, PDSA)
     * 
     * @param property Property name (e.g., "rounds")
     * @return Property value, or -1 if not supported
     */
    int getProperty(String property);
}

