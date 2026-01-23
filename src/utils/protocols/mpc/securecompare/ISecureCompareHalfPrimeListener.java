package utils.protocols.mpc.securecompare;

/**
 * Listener interface for Secure Compare Half Prime protocol completion.
 */
public interface ISecureCompareHalfPrimeListener {
    
    /**
     * Called when the secure compare half prime protocol completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The secret ID where the result shares are stored
     */
    void onSecureCompareHalfPrimeComplete(String protocolId, String resultSecretId);
}



