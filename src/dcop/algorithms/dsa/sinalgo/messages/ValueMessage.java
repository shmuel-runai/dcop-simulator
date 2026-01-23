package dcop.algorithms.dsa.sinalgo.messages;

import sinalgo.nodes.messages.Message;

/**
 * Message for DSA agents to communicate their selected values.
 */
public class ValueMessage extends Message {
    
    /**
     * ID of the sender agent.
     */
    public int senderID;
    
    /**
     * Value selected by the sender agent.
     */
    public int selectedValue;
    
    /**
     * Creates a new ValueMessage.
     * 
     * @param senderID ID of the sending agent
     * @param selectedValue Value selected by the sender
     */
    public ValueMessage(int senderID, int selectedValue) {
        this.senderID = senderID;
        this.selectedValue = selectedValue;
    }
    
    /**
     * Creates a clone of this message.
     * Required by Sinalgo framework.
     */
    @Override
    public Message clone() {
        return new ValueMessage(senderID, selectedValue);
    }
    
    @Override
    public String toString() {
        return "ValueMessage[from=" + senderID + ", value=" + selectedValue + "]";
    }
}
