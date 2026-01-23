package utils.protocols.mpc.secureiszero;

/**
 * Listener interface for Secure Is-Zero protocol completion.
 * 
 * Notifies when a secure zero-check completes.
 */
public interface ISecureIsZeroListener {
    
    /**
     * Called when the secure is-zero protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the result secret (1 if input was 0, 0 otherwise)
     */
    void onSecureIsZeroComplete(String protocolId, String resultSecretId);
}
