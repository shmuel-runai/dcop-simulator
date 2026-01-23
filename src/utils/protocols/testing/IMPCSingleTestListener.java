package utils.protocols.testing;

/**
 * Listener interface for MPCSingleTestProtocol completion.
 * 
 * Notifies when a single test iteration completes with pass/fail status.
 */
public interface IMPCSingleTestListener {
    
    /**
     * Called when a single MPC test iteration completes.
     * 
     * @param protocolId The unique ID of the completed test protocol
     * @param testNumber The test iteration number
     * @param passed true if the test passed (both add and multiply were correct), false otherwise
     */
    void onTestComplete(String protocolId, int testNumber, boolean passed);
}
