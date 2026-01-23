package utils.protocols.mpc.secureknowncompare;

/**
 * Listener interface for SecureKnownSecretCompareProtocol completion.
 */
public interface ISecureKnownSecretCompareListener {
    
    /**
     * Called when the secure known-secret comparison completes.
     * 
     * @param protocolId The protocol ID
     * @param resultSecretId The secret ID containing the result (1 if known < secret, 0 otherwise)
     */
    void onSecureKnownSecretCompareComplete(String protocolId, String resultSecretId);
}

