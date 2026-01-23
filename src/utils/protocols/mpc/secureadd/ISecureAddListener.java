package utils.protocols.mpc.secureadd;

/**
 * Listener interface for SecureAddProtocol completion.
 * 
 * Agents implement this interface to receive type-safe callbacks when
 * a secure addition protocol completes.
 */
public interface ISecureAddListener {
    
    /**
     * Called when a secure addition protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the resulting secret (sum)
     */
    void onSecureAddComplete(String protocolId, String resultSecretId);
}

