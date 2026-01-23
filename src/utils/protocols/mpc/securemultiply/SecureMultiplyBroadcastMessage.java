package utils.protocols.mpc.securemultiply;

import utils.protocols.core.IProtocolMessage;


/**
 * Broadcast message containing the reconstructed masked value.
 * 
 * Sent by the initiator to all participants with the reconstructed value
 * clean-c' = a*b + r (the product plus the r-secret).
 * 
 * Each participant will subtract their share of r to get their final share of c.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureMultiplyBroadcastMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    /**
     * The reconstructed masked value: a*b + r
     */
    public final long cleanCPrime;
    
    public SecureMultiplyBroadcastMessage(String protocolId, int senderId, long cleanCPrime) {
        this.protocolId = protocolId;
        this.protocolType = SecureMultiplyProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.cleanCPrime = cleanCPrime;
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
        return String.format("SecureMultiplyBroadcast[pid=%s, from=%d, clean-c'=%d]",
                           protocolId, senderId, cleanCPrime);
    }
}

