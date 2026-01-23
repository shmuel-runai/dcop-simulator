package dcop.algorithms.pdsa;

/**
 * Listener interface for P-DSA round protocol completion.
 * 
 * Notifies when a complete round of P-DSA has finished (all 6 phases).
 */
public interface IPDSARoundListener {
    
    /**
     * Called when a P-DSA round completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param roundNumber The round number that completed
     * @param newValue The agent's value after this round (may be unchanged)
     */
    void onRoundComplete(String protocolId, int roundNumber, int newValue);
}
