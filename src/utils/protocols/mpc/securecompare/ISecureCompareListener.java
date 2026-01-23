package utils.protocols.mpc.securecompare;

/**
 * Listener interface for Secure Compare protocol completion.
 * 
 * Notifies when a secure comparison completes and the result shares
 * have been distributed to all participants.
 */
public interface ISecureCompareListener {
    
    /**
     * Called when the secure comparison protocol completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The secret ID where the comparison result (0 or 1) shares are stored
     */
    void onSecureCompareComplete(String protocolId, String resultSecretId);
}

