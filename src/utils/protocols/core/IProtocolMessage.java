package utils.protocols.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Framework-agnostic interface for all protocol messages.
 * 
 * This interface defines the minimal contract for protocol messages,
 * with no dependencies on any specific framework (like Sinalgo).
 * 
 * Concrete message classes implement this interface as POJOs.
 * Framework adapters wrap these messages for transport (e.g., SinalgoProtocolMessageWrapper).
 */
public interface IProtocolMessage {
    
    /**
     * Gets the unique identifier for the protocol instance this message belongs to.
     * 
     * @return The protocol instance ID
     */
    String getProtocolId();
    
    /**
     * Gets the type of protocol (e.g., "SECURE_ADD_MPC", "RECONSTRUCT_SECRET").
     * 
     * @return The protocol type identifier
     */
    String getProtocolType();
    
    /**
     * Gets the ID of the agent sending this message.
     * 
     * @return The sender agent ID
     */
    int getSenderId();
    
    /**
     * Extracts protocol-specific parameters from this message.
     * Used by DistributedProtocolManager to initialize protocols from incoming messages.
     * 
     * @return Map of parameter name to value (default: empty map)
     */
    default Map<String, Object> extractParams() {
        return new HashMap<>();
    }
    
    /**
     * Returns true if this is a completion/acknowledgment message.
     * Completion messages should only be handled by existing protocols,
     * not used to create new protocol instances.
     * 
     * @return true if this is a completion message (default: false)
     */
    default boolean isCompletionMessage() {
        return false;
    }
}

