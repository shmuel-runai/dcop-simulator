package utils.protocols.mpc.distribution;

/**
 * Listener interface for ShareDistributionProtocol completion.
 * 
 * Agents implement this interface to receive type-safe callbacks when
 * a share distribution protocol completes.
 */
public interface IShareDistributionListener {
    
    /**
     * Called when a share distribution protocol completes successfully.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param secretId The identifier of the distributed secret
     */
    void onShareDistributionComplete(String protocolId, String secretId);
}

