package utils.protocols.mpc.securemin;

/**
 * Listener interface for Secure Min protocol completion.
 * 
 * Notifies when a secure minimum computation completes.
 */
public interface ISecureMinListener {
    
    /**
     * Called when the secure min protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the result secret containing min(left, right)
     */
    void onSecureMinComplete(String protocolId, String resultSecretId);
}

