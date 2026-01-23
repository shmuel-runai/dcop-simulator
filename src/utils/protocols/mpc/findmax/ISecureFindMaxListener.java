package utils.protocols.mpc.findmax;

/**
 * Listener interface for Secure FindMax protocol completion.
 * 
 * Notifies when the maximum value and its index have been found in an array of shared secrets.
 */
public interface ISecureFindMaxListener {
    
    /**
     * Called when the secure find-max protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param valueResultId The identifier of the maximum value share
     * @param indexResultId The identifier of the maximum index share
     */
    void onSecureFindMaxComplete(String protocolId, String valueResultId, String indexResultId);
}
