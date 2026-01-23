package utils.protocols.mpc.distribution;

/**
 * Listener interface for VectorShareDistributionProtocol completion.
 * 
 * Called when vector share distribution is complete (all participants have shares).
 */
public interface IVectorShareDistributionListener {
    
    /**
     * Called when vector share distribution completes successfully.
     * 
     * @param protocolId The protocol ID
     * @param baseSecretId The base secret ID (e.g., "E_5-r3" for E_5-r3[0], E_5-r3[1], ...)
     */
    void onVectorShareDistributionComplete(String protocolId, String baseSecretId);
}

