package utils.protocols.common.barrier;

/**
 * Listener interface for BarrierProtocol completion.
 * 
 * Called when all participants have signaled the barrier.
 */
public interface IBarrierListener {
    
    /**
     * Called when the barrier is complete (all participants have signaled).
     * 
     * @param barrierId The barrier protocol ID
     */
    void onBarrierComplete(String barrierId);
}
