package utils.protocols.mpc.securemultiply;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;


/**
 * Request message to initiate a Secure Multiplication protocol.
 * 
 * Broadcast by the initiator to all participants to start the multiply-and-add operation.
 * Contains all information needed for participants to perform local computation.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class SecureMultiplyRequestMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    public final String secretAId;
    public final String secretBId;
    public final String secretCId;
    public final String rSecretId;  // The r-secret used for masking
    public final long prime;
    public final String resultTag;  // null for sticky, non-null for tagged storage
    
    public SecureMultiplyRequestMessage(String protocolId, int senderId, 
                                       String secretAId, String secretBId, 
                                       String secretCId, String rSecretId, long prime,
                                       String resultTag) {
        this.protocolId = protocolId;
        this.protocolType = SecureMultiplyProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.secretAId = secretAId;
        this.secretBId = secretBId;
        this.secretCId = secretCId;
        this.rSecretId = rSecretId;
        this.prime = prime;
        this.resultTag = resultTag;
    }
    
    public String getResultTag() { return resultTag; }
    
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
        params.put("rSecretId", rSecretId);
        params.put("prime", Long.valueOf(prime));
        params.put("resultTag", resultTag);
        params.put("initiatorId", Integer.valueOf(senderId));
        return params;
    }
    
    @Override
    public String toString() {
        return String.format("SecureMultiplyRequest[pid=%s, from=%d, %s*%s=%s, r=%s]",
                           protocolId, senderId, secretAId, secretBId, secretCId, rSecretId);
    }
}

