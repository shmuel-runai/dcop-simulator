package utils.protocols.mpc.secureadd;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;

/**
 * Request message to initiate a Secure MPC Addition protocol.
 * 
 * Broadcast by the initiator to all participants to start the operation.
 * Contains all information needed for participants to perform local computation.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureAddRequestMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    public final String secretAId;
    public final String secretBId;
    public final String secretCId;
    public final long prime;
    public final String resultTag;  // null for sticky, non-null for tagged storage
    
    public SecureAddRequestMessage(String protocolId, int senderId, 
                                   String secretAId, String secretBId, 
                                   String secretCId, long prime, String resultTag) {
        this.protocolId = protocolId;
        this.protocolType = SecureAddProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.secretAId = secretAId;
        this.secretBId = secretBId;
        this.secretCId = secretCId;
        this.prime = prime;
        this.resultTag = resultTag;
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
        return String.format("SecureAddRequest[pid=%s, from=%d, %s+%s=%s]",
                           protocolId, senderId, secretAId, secretBId, secretCId);
    }
}

