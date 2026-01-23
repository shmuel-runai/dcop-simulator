package utils.protocols.mpc.securecompare;

/**
 * Listener interface for Secure LSB protocol completion.
 */
public interface ISecureLSBListener {
    
    /**
     * Called when the secure LSB extraction protocol completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param resultSecretId The secret ID where the LSB result shares are stored
     */
    void onSecureLSBComplete(String protocolId, String resultSecretId);
}



