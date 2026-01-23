package utils.protocols.adapters.sinalgo;

import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.runtime.Runtime;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.IProtocolMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Sinalgo adapter for protocol message transport.
 * 
 * This adapter bridges the framework-agnostic protocol library
 * with Sinalgo's node messaging system.
 * 
 * Wraps protocol messages in SinalgoProtocolMessageWrapper for transport.
 * Uses Sinalgo's sendDirect() method to ensure reliable delivery.
 * 
 * <h2>Design Note: Network Topology</h2>
 * This transport respects Sinalgo's network topology by using {@code outgoingConnections}
 * 
 * <p>For MPC/DCOP protocols that require full mesh communication, ensure:
 * <ul>
 *   <li>Connections are created manually in CustomGlobal (e.g., full mesh)</li>
 *   <li>DefaultConnectivityModel is set to "StaticConnectivity" to preserve connections</li>
 * </ul>
 * 
 * <p>If connections aren't properly configured, {@link #sendMessage} will throw
 * a RuntimeException when trying to send to an unconnected node.
 */
public class SinalgoMessageTransport implements IMessageTransport {
    
    /**
     * The Sinalgo node that this transport belongs to.
     */
    private final Node node;
    
    /**
     * Callback for handling messages sent to self (local delivery).
     */
    private BiConsumer<IProtocolMessage, Integer> localCallback;
    
    /**
     * Cached participants list (neighbors + self).
     * Lazily initialized on first call to getParticipants().
     */
    private List<Integer> cachedParticipants;
    
    /**
     * Creates a new Sinalgo message transport for a node.
     * 
     * @param node The Sinalgo node
     */
    public SinalgoMessageTransport(Node node) {
        this.node = node;
    }
    
    @Override
    public int getLocalId() {
        return node.ID;
    }
    
    @Override
    public void setLocalMessageCallback(BiConsumer<IProtocolMessage, Integer> callback) {
        this.localCallback = callback;
    }
    
    @Override
    public void sendMessage(IProtocolMessage msg, int recipientId) {
        // Self-message with local callback - handle locally (synchronous)
        if (recipientId == node.ID && localCallback != null) {
            localCallback.accept(msg, node.ID);
            return;
        }
        
        // Find the recipient node by ID (for self without callback, use node directly)
        Node recipient = (recipientId == node.ID) ? node : findNodeById(recipientId);
        
        if (recipient != null) {
            // Wrap protocol message for Sinalgo transport
            SinalgoProtocolMessageWrapper wrapper = new SinalgoProtocolMessageWrapper(msg);
            // Use sendDirect for reliable, direct delivery
            node.sendDirect(wrapper, recipient);
        } else {
            throw new RuntimeException("Could not find recipient node with ID " + recipientId 
                + " from node " + node.ID + " (outgoingConnections.size=" + node.outgoingConnections.size() + ")");
        }
    }
    
    /**
     * Returns IDs of neighbor nodes (excludes self).
     * Self is added by {@link #getParticipants()}.
     * 
     * Uses outgoing connections first, falls back to Runtime.nodes if empty.
     */
    @Override
    public List<Integer> neighborsId() {
        List<Integer> neighborIds = new ArrayList<>();
        
        // Try outgoing connections first (respects topology)
        for (Edge edge : node.outgoingConnections) {
            neighborIds.add(edge.endNode.ID);
        }
        
        // Fallback: if no connections, use all runtime nodes (minus self)
        if (neighborIds.isEmpty()) {
            for (Node n : Runtime.nodes) {
                if (n.ID != node.ID) {
                    neighborIds.add(n.ID);
                }
            }
        }
        
        Collections.sort(neighborIds);
        return neighborIds;
    }
    
    /**
     * Overrides the default implementation to add caching.
     * Caching is deferred until connections are established (non-empty neighbor list).
     */
    @Override
    public List<Integer> getParticipants() {
        // Check if we have a valid cached list
        if (cachedParticipants != null) {
            return cachedParticipants;
        }
        
        // Build the participants list
        List<Integer> neighbors = neighborsId();
        
        // Only cache if we have neighbors (connections established)
        // Otherwise return fresh list each time until connections exist
        List<Integer> participants = new ArrayList<>(neighbors);
        if (!participants.contains(node.ID)) {
            participants.add(node.ID);
        }
        Collections.sort(participants);
        
        // Cache only if we have neighbors
        if (!neighbors.isEmpty()) {
            cachedParticipants = Collections.unmodifiableList(participants);
            return cachedParticipants;
        }
        
        return participants;
    }
    
    // broadcast() and multicast() methods are inherited from interface defaults
    
    /**
     * Finds a remote node by its ID using outgoing connections.
     * Note: Self is already handled by sendMessage(), so this only finds OTHER nodes.
     * 
     * @param nodeId The node ID to find
     * @return The node, or null if not connected
     */
    private Node findNodeById(int nodeId) {
        for (Edge edge : node.outgoingConnections) {
            if (edge.endNode.ID == nodeId) {
                return edge.endNode;
            }
        }
        return null;
    }
}

