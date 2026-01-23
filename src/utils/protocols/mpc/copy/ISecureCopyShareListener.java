package utils.protocols.mpc.copy;

/**
 * Listener interface for SecureCopyShare protocol completion.
 * Notified when a share copy operation completes across all participants.
 */
public interface ISecureCopyShareListener {
    
    /**
     * Called when the secure copy share protocol completes.
     * 
     * @param protocolId The unique protocol ID
     * @param srcSecretId The source secret ID that was copied from
     * @param dstSecretId The destination secret ID that was copied to
     */
    void onSecureCopyShareComplete(String protocolId, String srcSecretId, String dstSecretId);
}

