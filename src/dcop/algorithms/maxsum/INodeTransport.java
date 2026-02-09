package dcop.algorithms.maxsum;

import sinalgo.nodes.messages.Message;

/**
 * Transport abstraction for sending messages.
 * Decouples brain logic from Sinalgo Node references.
 * 
 * Implemented by MaxSumNode, allowing brains to send messages
 * without knowing about Sinalgo internals.
 */
public interface INodeTransport {
    
    /**
     * Send a message to a specific node by its Sinalgo ID.
     * 
     * @param msg The message to send
     * @param targetNodeId The Sinalgo node ID of the recipient
     */
    void sendMessage(Message msg, int targetNodeId);
    
    /**
     * Get this node's Sinalgo ID.
     * 
     * @return The Sinalgo node ID
     */
    int getNodeId();
}
