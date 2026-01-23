package utils.protocols.mpc.dotproduct;

/**
 * Listener interface for SecureDotProduct protocol completion.
 * 
 * Called when the dot product of two secret-shared vectors is complete.
 */
public interface ISecureDotProductListener {
    
    /**
     * Called when a secure dot product protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultId The identifier of the resulting secret (dot product)
     */
    void onSecureDotProductComplete(String protocolId, String resultId);
}



