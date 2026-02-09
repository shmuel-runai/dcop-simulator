package dcop.algorithms.pmaxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Function node to Agent node.
 * Contains R values (beliefs about optimal value for each domain index).
 * 
 * R values are split into local (held by function) and remote (held by agent).
 * The sum local + remote = actual R value.
 */
public class InjectRsMessage extends Message {
    
    /** Round number */
    public final int round;
    
    /** Source agent ID (the "other" agent from the function's perspective) */
    public final int source;
    
    /** Target agent ID (the recipient) */
    public final int target;
    
    /** Local R values (encrypted with F_target) */
    public final BigInteger[] local;
    
    /** Remote R values (plaintext, to be stored by agent) */
    public final BigInteger[] remote;
    
    /** Whether this message includes remote values */
    public final boolean withRemote;
    
    public InjectRsMessage(int round, int source, int target, 
                           BigInteger[] local, BigInteger[] remote, boolean withRemote) {
        this.round = round;
        this.source = source;
        this.target = target;
        this.local = Arrays.copyOf(local, local.length);
        this.remote = (remote != null && withRemote) ? Arrays.copyOf(remote, remote.length) : null;
        this.withRemote = withRemote;
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("InjectRsMessage(round=%d, src=%d, tgt=%d, withRemote=%s, len=%d)",
                round, source, target, withRemote, local.length);
    }
    
    public String toString(boolean full) {
        if (!full) return toString();
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("InjectRsMessage(round=%d, src=%d, tgt=%d, withRemote=%s)\n",
                round, source, target, withRemote));
        sb.append("  local: [");
        for (int i = 0; i < local.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(local[i]);
        }
        sb.append("]\n");
        if (remote != null) {
            sb.append("  remote: [");
            for (int i = 0; i < remote.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(remote[i]);
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
