package utils.protocols.mpc.securemultiply;

import utils.crypto.secretsharing.Share;
import utils.protocols.core.IProtocolMessage;


/**
 * Response message containing the masked share (share_c') for multiplication.
 * 
 * Sent by each participant back to the initiator with their computed
 * share_c' = (share_a * share_b + share_r) mod prime
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureMultiplyShareResponseMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    /**
     * The masked share: (share_a * share_b + share_r) mod prime
     * Contains both value and secret (for debugging).
     */
    public final Share shareCPrime;
    
    public SecureMultiplyShareResponseMessage(String protocolId, int senderId, Share shareCPrime) {
        this.protocolId = protocolId;
        this.protocolType = SecureMultiplyProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.shareCPrime = shareCPrime;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return protocolType;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public String toString() {
        return String.format("SecureMultiplyShareResponse[pid=%s, from=%d, share'=%s]",
                           protocolId, senderId, shareCPrime);
    }
}
