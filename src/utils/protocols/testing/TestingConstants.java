package utils.protocols.testing;

/**
 * Constants for testing/orchestration protocol types.
 */
public class TestingConstants {
    
    /**
     * MPC Single Test protocol type.
     * Orchestrates a complete test iteration of MPC operations.
     */
    public static final String MPC_SINGLE_TEST = "MPC_SINGLE_TEST";
    
    /**
     * MPC Array Test protocol type.
     * Tests array-based MPC operations like FindMin.
     */
    public static final String MPC_ARRAY_TEST = "MPC_ARRAY_TEST";
    
    // Private constructor to prevent instantiation
    private TestingConstants() {
        throw new AssertionError("Cannot instantiate TestingConstants");
    }
}
