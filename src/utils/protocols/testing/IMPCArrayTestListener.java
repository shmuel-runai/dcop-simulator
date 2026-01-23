package utils.protocols.testing;

/**
 * Listener interface for MPC Array Test protocol completion.
 * 
 * Notifies when an array-based MPC test (e.g., FindMin) completes.
 */
public interface IMPCArrayTestListener {
    
    /**
     * Called when an array test completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param testNumber The test iteration number
     * @param passed Whether the test passed verification
     */
    void onArrayTestComplete(String protocolId, int testNumber, boolean passed);
}
