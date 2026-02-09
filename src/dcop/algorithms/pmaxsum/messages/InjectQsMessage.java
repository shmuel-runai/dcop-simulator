package dcop.algorithms.pmaxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Agent node to Function node.
 * Contains Q values (sum of R values from other neighbors, excluding target).
 * 
 * Q values are split into:
 * - local: encrypted with E_agent (agent's encryption key)
 * - remote: product of encrypted R values (encrypted with F_agent)
 */
public class InjectQsMessage extends Message {
    
    /** Round number */
    public final int round;
    
    /** Source agent ID (the sender) */
    public final int source;
    
    /** Target agent ID (the "other" agent connected to this function) */
    public final int target;
    
    /** Local Q values (encrypted with E_source) */
    public final BigInteger[] local;
    
    /** Remote Q values (product of encrypted R's, encrypted with F_source) */
    public final BigInteger[] remote;
    
    public InjectQsMessage(int round, int source, int target, 
                           BigInteger[] local, BigInteger[] remote) {
        this.round = round;
        this.source = source;
        this.target = target;
        this.local = Arrays.copyOf(local, local.length);
        this.remote = Arrays.copyOf(remote, remote.length);
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("InjectQsMessage(round=%d, src=%d, tgt=%d, len=%d)",
                round, source, target, local.length);
    }
    
    public String toString(boolean full) {
        if (!full) return toString();
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("InjectQsMessage(round=%d, src=%d, tgt=%d)\n",
                round, source, target));
        sb.append("  local: [");
        for (int i = 0; i < local.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(local[i]);
        }
        sb.append("]\n");
        sb.append("  remote: [");
        for (int i = 0; i < remote.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(remote[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
