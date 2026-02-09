package dcop.common.configuration;

/**
 * Enum representing different network topology types for DCOP simulations.
 */
public enum NetworkType {
    /**
     * Random network - edges are created with probability = networkDensity.
     */
    RANDOM,
    
    /**
     * Scale-free network - uses Barabási-Albert preferential attachment model.
     * Creates networks with power-law degree distribution.
     */
    SCALE_FREE;
    
    /**
     * Parse network type from string (case-insensitive).
     * 
     * @param name The network type name
     * @return The corresponding NetworkType
     * @throws IllegalArgumentException if name is not recognized
     */
    public static NetworkType fromString(String name) {
        if (name == null) {
            return RANDOM; // Default
        }
        
        String normalized = name.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        
        try {
            return NetworkType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try alternative names
            if (normalized.equals("SCALEFREE") || normalized.equals("BARABASI_ALBERT") || normalized.equals("BA")) {
                return SCALE_FREE;
            }
            throw new IllegalArgumentException(
                "Unknown network type: " + name + ". Valid options: RANDOM, SCALE_FREE");
        }
    }
}
