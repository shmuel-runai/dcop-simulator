package dcop.algorithms.maxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Function node to Agent node in vanilla Max-Sum.
 * Contains R values (computed min over Q + cost for each domain index).
 */
public class UpdateRsMessage extends Message {
    
    /** The other agent ID (identifies which function this R came from) */
    public final int otherId;
    
    /** Round number */
    public final int round;
    
    /** R values for each domain index */
    public final BigInteger[] r;
    
    public UpdateRsMessage(int otherId, int round, BigInteger[] r) {
        this.otherId = otherId;
        this.round = round;
        this.r = Arrays.copyOf(r, r.length);
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("UpdateRsMessage(other=%d, round=%d, len=%d)", otherId, round, r.length);
    }
    
    public String rString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(r[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
