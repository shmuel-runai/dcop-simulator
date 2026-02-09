package dcop.algorithms.pmaxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Function node to Agent node.
 * Contains the W matrix for min computation (Protocol 2).
 * 
 * The W matrix is [targetDomainSize x otherDomainSize].
 * Each cell contains an encrypted value that the agent must decrypt
 * to find the minimum in each row.
 * 
 * The shifter is an opaque value used to hide the actual minimum.
 */
public class ProcessWsRequestMessage extends Message {
    
    /** Round number */
    public final int round;
    
    /** Source agent ID (the agent whose Q's were used) */
    public final int source;
    
    /** Target agent ID (the agent who will compute the min) */
    public final int target;
    
    /** W matrix: [targetDomain][otherDomain] encrypted values */
    public final BigInteger[][] Ws;
    
    /** Random shifter used to hide the actual values */
    public final BigInteger shifter;
    
    public ProcessWsRequestMessage(int round, int source, int target, 
                                    BigInteger[][] Ws, BigInteger shifter) {
        this.round = round;
        this.source = source;
        this.target = target;
        this.Ws = new BigInteger[Ws.length][];
        for (int i = 0; i < Ws.length; i++) {
            this.Ws[i] = Arrays.copyOf(Ws[i], Ws[i].length);
        }
        this.shifter = shifter;
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("ProcessWsRequestMessage(round=%d, src=%d, tgt=%d, dims=%dx%d)",
                round, source, target, Ws.length, Ws.length > 0 ? Ws[0].length : 0);
    }
}
