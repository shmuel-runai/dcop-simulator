package dcop.algorithms.pmaxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Agent node back to Function node.
 * Contains the computed R values after finding min in each row of W matrix.
 * 
 * The response includes the shifter (opaque) so the function can
 * recover the actual R values.
 */
public class ProcessWsResponseMessage extends Message {
    
    /** Round number */
    public final int round;
    
    /** Affinity - which agent's perspective this R is from */
    public final int affinity;
    
    /** Source agent ID (the function's source agent) */
    public final int source;
    
    /** Target agent ID (the function's target agent) */
    public final int target;
    
    /** Computed values: min - r for each row */
    public final BigInteger[] values;
    
    /** The shifter used in the request (opaque) */
    public final BigInteger shifter;
    
    public ProcessWsResponseMessage(int round, int affinity, int source, int target,
                                     BigInteger[] values, BigInteger shifter) {
        this.round = round;
        this.affinity = affinity;
        this.source = source;
        this.target = target;
        this.values = Arrays.copyOf(values, values.length);
        this.shifter = shifter;
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("ProcessWsResponseMessage(round=%d, affinity=%d, src=%d, tgt=%d, len=%d)",
                round, affinity, source, target, values.length);
    }
    
    public String toString(boolean full) {
        if (!full) return toString();
        
        StringBuilder sb = new StringBuilder();
        sb.append(toString()).append("\n  values: [");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
