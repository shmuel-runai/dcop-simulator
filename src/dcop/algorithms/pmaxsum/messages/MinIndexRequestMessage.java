package dcop.algorithms.pmaxsum.messages;

import java.math.BigInteger;
import java.util.Arrays;
import sinalgo.nodes.messages.Message;

/**
 * Message sent from Agent to Function in the final round.
 * Contains the M values (sum of all R values) for each domain index.
 * 
 * The function will decrypt these and find the index of the minimum.
 */
public class MinIndexRequestMessage extends Message {
    
    /** Source agent ID */
    public final int source;
    
    /** M values: encrypted sum of all R's for each domain index */
    public final BigInteger[] m;
    
    public MinIndexRequestMessage(int source, BigInteger[] m) {
        this.source = source;
        this.m = Arrays.copyOf(m, m.length);
    }
    
    @Override
    public Message clone() {
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("MinIndexRequestMessage(src=%d, len=%d)", source, m.length);
    }
}
