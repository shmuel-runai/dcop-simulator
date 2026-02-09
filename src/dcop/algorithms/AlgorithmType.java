package dcop.algorithms;

/**
 * Enumeration of available DCOP algorithms.
 */
public enum AlgorithmType {
    /**
     * Basic random algorithm - each agent selects a random value.
     */
    BASIC,
    
    /**
     * DSA (Distributed Stochastic Algorithm) - local search with probabilistic improvements.
     */
    DSA,
    
    /**
     * P-DSA (Private Distributed Stochastic Algorithm) - privacy-preserving DSA using MPC.
     */
    PDSA,
    
    /**
     * P-MGM (Private Maximum Gain Message) - privacy-preserving MGM using MPC.
     */
    PMGM,
    
    /**
     * P-MAXSUM (Private Max-Sum) - privacy-preserving Max-Sum using Paillier encryption.
     */
    PMAXSUM,
    
    /**
     * MAXSUM (Max-Sum) - vanilla Max-Sum algorithm (no privacy protection).
     */
    MAXSUM;
    
    /**
     * Parse algorithm type from string (case-insensitive).
     * 
     * @param name Algorithm name
     * @return AlgorithmType enum value
     * @throws IllegalArgumentException if name is not recognized
     */
    public static AlgorithmType fromString(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Algorithm name cannot be null");
        }
        
        try {
            return AlgorithmType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown algorithm: " + name + ". Valid options: BASIC, DSA, PDSA, PMGM, PMAXSUM, MAXSUM");
        }
    }
}

