package dcop.algorithms.maxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Agent node to Function node in vanilla Max-Sum.
 * Contains Q values (sum of R values from other neighbors, excluding target).
 */
public class UpdateQsMessage extends Message {
    
    /** The target agent ID (the "other" agent connected to this function) */
    public final int targetId;
    
    /** Round number */
    public final int round;
    
    /** Q values for each domain index */
    public final BigInteger[] q;
    
    public UpdateQsMessage(int targetId, int round, BigInteger[] q) {
        this.targetId = targetId;
        this.round = round;
        this.q = Arrays.copyOf(q, q.length);
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("UpdateQsMessage(target=%d, round=%d, len=%d)", targetId, round, q.length);
    }
    
    public String qString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < q.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(q[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
