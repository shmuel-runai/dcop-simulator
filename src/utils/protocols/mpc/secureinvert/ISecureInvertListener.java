package utils.protocols.mpc.secureinvert;

/**
 * Listener interface for Secure Invert protocol completion.
 * 
 * Notifies when a secure inversion (1 - x) completes.
 */
public interface ISecureInvertListener {
    
    /**
     * Called when a secure invert protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the result secret (1 - input)
     */
    void onSecureInvertComplete(String protocolId, String resultSecretId);
}

