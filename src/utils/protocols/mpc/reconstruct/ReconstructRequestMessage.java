package utils.protocols.mpc.reconstruct;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;


/**
 * Request message to initiate a Secret Reconstruction protocol.
 * 
 * Broadcast by the initiator to all participants to request their shares
 * for reconstruction. Only the initiator will learn the reconstructed value.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class ReconstructRequestMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    public final String secretId;
    public final long prime;
    
    public ReconstructRequestMessage(String protocolId, int senderId, 
                                    String secretId, long prime) {
        this.protocolId = protocolId;
        this.protocolType = ReconstructSecretProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.secretId = secretId;
        this.prime = prime;
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
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("secretId", secretId);
        params.put("prime", Long.valueOf(prime));
        params.put("initiatorId", Integer.valueOf(senderId));
        return params;
    }
    
    // Getters for encapsulation
    public String getSecretId() { return secretId; }
    public long getPrime() { return prime; }
    
    @Override
    public String toString() {
        return String.format("ReconstructRequest[pid=%s, from=%d, secret=%s]",
                           protocolId, senderId, secretId);
    }
}

