package utils.protocols.adapters.sinalgo;

import sinalgo.nodes.messages.Message;
import utils.protocols.core.IProtocolMessage;

/**
 * Wrapper that adapts framework-agnostic IProtocolMessage to Sinalgo's Message.
 * 
 * This adapter allows our protocol messages (which are POJOs) to be transported
 * through Sinalgo's messaging system which requires extending sinalgo.nodes.messages.Message.
 * 
 * The wrapper pattern keeps the protocol layer completely independent of Sinalgo,
 * while still allowing seamless integration with the Sinalgo framework.
 */
public class SinalgoProtocolMessageWrapper extends Message {
    
    /**
     * The wrapped protocol message.
     */
    private final IProtocolMessage protocolMessage;
    
    /**
     * Creates a new wrapper around a protocol message.
     * 
     * @param protocolMessage The protocol message to wrap
     */
    public SinalgoProtocolMessageWrapper(IProtocolMessage protocolMessage) {
        this.protocolMessage = protocolMessage;
    }
    
    /**
     * Unwraps and returns the protocol message.
     * 
     * @return The wrapped protocol message
     */
    public IProtocolMessage unwrap() {
        return protocolMessage;
    }
    
    /**
     * Required by Sinalgo: creates a clone of this wrapper.
     * Since our protocol messages are immutable POJOs, we just wrap the same instance.
     */
    @Override
    public Message clone() {
        return new SinalgoProtocolMessageWrapper(protocolMessage);
    }
    
    @Override
    public String toString() {
        return "SinalgoWrapper[" + protocolMessage + "]";
    }
}

