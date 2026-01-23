package utils.protocols.mpc.findmin;

/**
 * Listener interface for Secure FindMin protocol completion.
 * 
 * Notifies when the minimum value and index have been found in an array of shared secrets.
 */
public interface ISecureFindMinListener {
    
    /**
     * Called when the secure find-min protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param valueResultId The identifier of the minimum value share
     * @param indexResultId The identifier of the minimum index share
     */
    void onSecureFindMinComplete(String protocolId, String valueResultId, String indexResultId);
}
