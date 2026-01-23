package dcop.algorithms.pmgm;

/**
 * Listener interface for P-MGM round protocol completion.
 * 
 * Notifies when a complete round of P-MGM has finished.
 */
public interface IPMGMRoundListener {
    
    /**
     * Called when a P-MGM round completes.
     * 
     * @param protocolId The unique ID of the completed protocol
     * @param roundNumber The round number that completed
     * @param newValue The agent's value after this round (may be unchanged)
     */
    void onRoundComplete(String protocolId, int roundNumber, int newValue);
}
