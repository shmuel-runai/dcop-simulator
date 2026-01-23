package utils.protocols.mpc.secureknownsub;

/**
 * Listener interface for Secure Known-Value Subtraction protocol completion.
 * 
 * This protocol performs subtraction where one operand is a known (public) value
 * and the other is a shared secret.
 */
public interface ISecureKnownSubListener {
    
    /**
     * Called when the secure known-value subtraction protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The identifier of the result secret
     */
    void onSecureKnownSubComplete(String protocolId, String resultSecretId);
}
