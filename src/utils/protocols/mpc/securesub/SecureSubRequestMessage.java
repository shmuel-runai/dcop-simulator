package utils.protocols.mpc.securesub;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;

/**
 * Request message to initiate secure subtraction.
 * 
 * Sent by the initiator to all participants to start the subtraction protocol.
 */
public class SecureSubRequestMessage implements IProtocolMessage {
    
    public final String protocolId;
    public final String secretAId;  // Minuend
    public final String secretBId;  // Subtrahend
    public final String secretCId;  // Difference (result)
    public final long prime;
    public final int senderId;
    public final String resultTag;  // null for sticky, non-null for tagged storage
    
    public SecureSubRequestMessage(String protocolId, String secretAId, String secretBId, 
                                   String secretCId, long prime, int senderId, String resultTag) {
        this.protocolId = protocolId;
        this.secretAId = secretAId;
        this.secretBId = secretBId;
        this.secretCId = secretCId;
        this.prime = prime;
        this.senderId = senderId;
        this.resultTag = resultTag;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureSubProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("secretAId", secretAId);
        params.put("secretBId", secretBId);
        params.put("secretCId", secretCId);
        params.put("prime", Long.valueOf(prime));
        params.put("resultTag", resultTag);
        params.put("initiatorId", Integer.valueOf(senderId));
        return params;
    }
    
    // Getters for encapsulation
    public String getSecretAId() { return secretAId; }
    public String getSecretBId() { return secretBId; }
    public String getSecretCId() { return secretCId; }
    public long getPrime() { return prime; }
    public String getResultTag() { return resultTag; }
    
    @Override
    public String toString() {
        return "SecureSubRequest{protocolId=" + protocolId + 
               ", a=" + secretAId + ", b=" + secretBId + ", c=" + secretCId + "}";
    }
}

