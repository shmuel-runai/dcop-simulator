package utils.protocols.mpc.reconstruct;

/**
 * Listener interface for ReconstructSecretProtocol completion.
 * 
 * Agents implement this interface to receive type-safe callbacks when
 * a secret reconstruction protocol completes.
 */
public interface IReconstructListener {
    
    /**
     * Called when a secret reconstruction protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param secretId The identifier of the reconstructed secret
     * @param reconstructedValue The actual reconstructed secret value
     */
    void onReconstructComplete(String protocolId, String secretId, long reconstructedValue);
}

