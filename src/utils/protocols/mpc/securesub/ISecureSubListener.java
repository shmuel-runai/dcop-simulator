package utils.protocols.mpc.securesub;

/**
 * Listener interface for Secure Subtraction protocol completion.
 * 
 * Agents implement this interface to receive type-safe callbacks when
 * a secure subtraction protocol completes.
 */
public interface ISecureSubListener {
    
    /**
     * Called when a secure subtraction protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the result secret (a - b)
     */
    void onSecureSubComplete(String protocolId, String resultSecretId);
}

