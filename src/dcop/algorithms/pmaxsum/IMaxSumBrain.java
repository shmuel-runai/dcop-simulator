package dcop.algorithms.pmaxsum;

import sinalgo.nodes.messages.Message;

/**
 * Interface for Max-Sum brain implementations.
 * Implemented by both AgentBrain and FunctionBrain.
 * 
 * The brain contains all algorithm logic, decoupled from Sinalgo.
 * Communication happens via INodeTransport.
 */
public interface IMaxSumBrain {
    
    /**
     * Initialize the brain (called once after construction and wiring).
     */
    void init();
    
    /**
     * Start the algorithm (called when simulation begins).
     */
    void start();
    
    /**
     * Handle an incoming message.
     * 
     * @param msg The message received
     * @param senderNodeId The Sinalgo node ID of the sender
     */
    void handleMessage(Message msg, int senderNodeId);
    
    /**
     * Check if this brain has completed its work.
     * 
     * @return true if done, false if still running
     */
    boolean isDone();
    
    /**
     * Get the selected value (for agents) or -1 (for functions).
     * 
     * @return Selected domain index, or -1 for function nodes
     */
    int getAssignment();
    
    /**
     * Get current round number.
     * 
     * @return Current round (0-based)
     */
    int getRound();
    
    /**
     * Log current state for debugging.
     */
    void logState();
    
    /**
     * Get the agent index (1..N) or a function identifier.
     * For agents: returns the DCOP agent ID (1..N)
     * For functions: returns a unique identifier (could be negative or encoded)
     * 
     * @return The index/identifier
     */
    int getIndex();
    
    /**
     * Check if this is an agent brain (vs function brain).
     * 
     * @return true for AgentBrain, false for FunctionBrain
     */
    boolean isAgent();
    
    /**
     * Called each simulation step (from preStep).
     * This is where message sending can safely happen.
     */
    void tick();
}
