package utils.protocols.mpc.securemultiply;

/**
 * Listener interface for SecureMultiply protocol completion.
 * 
 * Agents implement this interface to receive type-safe callbacks when
 * a secure multiplication protocol completes.
 */
public interface ISecureMultiplyListener {
    
    /**
     * Called when a secure multiplication protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the resulting secret (product)
     */
    void onSecureMultiplyComplete(String protocolId, String resultSecretId);
}

