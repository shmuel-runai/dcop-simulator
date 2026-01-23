package utils.protocols.core;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Communication abstraction for sending protocol messages.
 * 
 * This interface decouples the protocol framework from specific
 * simulation/networking frameworks (like Sinalgo).
 * 
 * Each agent provides an implementation of this interface that
 * adapts the framework's messaging system.
 * 
 * Supports self-message optimization: when sending to self (recipientId == localId),
 * the transport can invoke a local callback instead of network transport.
 */
public interface IMessageTransport {
    
    /**
     * Get the local agent's ID.
     * Used for detecting self-messages.
     * 
     * @return The local agent's ID
     */
    int getLocalId();
    
    /**
     * Set a callback for handling messages sent to self.
     * When sendMessage() is called with recipientId == getLocalId(),
     * the callback is invoked instead of network transport.
     * 
     * @param callback BiConsumer receiving (message, senderId)
     */
    void setLocalMessageCallback(BiConsumer<IProtocolMessage, Integer> callback);
    
    /**
     * Send a protocol message to a specific recipient (unicast).
     * If recipientId == getLocalId() and a local callback is set,
     * the callback is invoked instead of network transport.
     * 
     * @param msg The protocol message to send
     * @param recipientId The ID of the recipient agent
     */
    void sendMessage(IProtocolMessage msg, int recipientId);
    
    /**
     * Get all neighbor agent IDs that this transport can reach.
     * Framework-specific implementation determines what "neighbors" means
     * (could be all agents in the system, or only directly connected nodes).
     * 
     * @return List of all reachable neighbor agent IDs
     */
    List<Integer> neighborsId();
    
    /**
     * Get the full participants list (neighbors + self).
     * 
     * Default implementation builds from neighborsId() + getLocalId().
     * Implementations should consider overriding this method to cache
     * the result for efficiency, as it may be called frequently.
     * 
     * @return List of all participant agent IDs including self
     */
    default List<Integer> getParticipants() {
        List<Integer> participants = new java.util.ArrayList<>(neighborsId());
        int localId = getLocalId();
        if (!participants.contains(localId)) {
            participants.add(localId);
        }
        return participants;
    }
    
    /**
     * Send a protocol message to all neighbors (true broadcast).
     * Default implementation uses neighborsId().
     * 
     * @param msg The protocol message to broadcast
     */
    default void broadcast(IProtocolMessage msg) {
        for (Integer neighborId : neighborsId()) {
            sendMessage(msg, neighborId);
        }
    }
    
    /**
     * Send a protocol message to a specific list of recipients (multicast).
     * Default implementation calls sendMessage for each recipient.
     * 
     * @param msg The protocol message to multicast
     * @param recipientIds List of recipient agent IDs
     */
    default void multicast(IProtocolMessage msg, List<Integer> recipientIds) {
        for (Integer recipientId : recipientIds) {
            sendMessage(msg, recipientId);
        }
    }
}
