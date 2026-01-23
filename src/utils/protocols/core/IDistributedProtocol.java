package utils.protocols.core;

import java.util.Map;

/**
 * Base interface for all distributed protocols.
 * 
 * Protocols are event-driven and process messages as they arrive.
 * Each protocol instance has a unique ID and manages its own state.
 * 
 * Protocols can be symmetric (all agents do the same thing) or asymmetric
 * (initiator vs participants have different logic).
 */
public interface IDistributedProtocol {
    
    /**
     * Initialize the protocol with parameters.
     * Called once when the protocol is created.
     * 
     * @param params Protocol-specific parameters (e.g., secret IDs, prime, etc.)
     */
    void initialize(Map<String, Object> params);
    
    /**
     * Handle an incoming protocol message.
     * This is the main event-driven entry point.
     * 
     * @param msg The protocol message received
     * @param senderId The ID of the agent who sent the message
     */
    void handleMessage(IProtocolMessage msg, int senderId);
    
    /**
     * Check if the protocol has completed.
     * 
     * @return true if the protocol execution is complete (success or failure)
     */
    boolean isComplete();
    
    /**
     * Check if the protocol completed successfully.
     * Only meaningful if isComplete() returns true.
     * 
     * @return true if protocol completed successfully, false if failed
     */
    boolean isSuccessful();
    
    /**
     * Get the unique identifier for this protocol instance.
     * 
     * @return The protocol instance ID
     */
    String getProtocolId();
    
    /**
     * Get the protocol type (e.g., "SECURE_ADD_MPC", "RECONSTRUCT_SECRET").
     * 
     * @return The protocol type identifier
     */
    String getProtocolType();
    
    /**
     * Get the result of the protocol execution.
     * Only meaningful if isComplete() and isSuccessful() return true.
     * The type of result depends on the specific protocol.
     * 
     * @return The protocol result, or null if no result or not complete
     */
    Object getResult();
}

