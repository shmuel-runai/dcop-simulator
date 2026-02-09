package dcop.algorithms.pmaxsum.messages;

import sinalgo.nodes.messages.Message;

/**
 * Message sent from Function to Agent in the final round.
 * Contains the index of the minimum value (the selected domain value).
 */
public class MinIndexResponseMessage extends Message {
    
    /** The index of the minimum value in the M array */
    public final int index;
    
    public MinIndexResponseMessage(int index) {
        this.index = index;
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("MinIndexResponseMessage(index=%d)", index);
    }
}
