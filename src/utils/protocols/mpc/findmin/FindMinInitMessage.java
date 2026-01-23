package utils.protocols.mpc.findmin;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;


/**
 * Initialization message for Secure FindMin protocol.
 * 
 * Broadcast by initiator to all agents to start the find-min operation.
 */
public class FindMinInitMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String baseArrayId;
    private final int firstIndex;
    private final int lastIndex;
    private final String vId;
    private final String kId;
    private final long prime;
    private final int threshold;
    private final String rSecretId;
    private final String storageTag;
    
    public FindMinInitMessage(String protocolId, String baseArrayId, int firstIndex, int lastIndex,
                             String vId, String kId, long prime, int threshold, String rSecretId,
                             String storageTag) {
        this.protocolId = protocolId;
        this.baseArrayId = baseArrayId;
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.vId = vId;
        this.kId = kId;
        this.prime = prime;
        this.threshold = threshold;
        this.rSecretId = rSecretId;
        this.storageTag = storageTag;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureFindMinProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return -1; // Will be set by transport layer
    }
    
    public String getBaseArrayId() {
        return baseArrayId;
    }
    
    public int getFirstIndex() {
        return firstIndex;
    }
    
    public int getLastIndex() {
        return lastIndex;
    }
    
    public String getVId() {
        return vId;
    }
    
    public String getKId() {
        return kId;
    }
    
    public long getPrime() {
        return prime;
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    public String getRSecretId() {
        return rSecretId;
    }
    
    public String getStorageTag() {
        return storageTag;
    }
    
    @Override
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("baseArrayId", baseArrayId);
        params.put("firstIndex", firstIndex);
        params.put("lastIndex", lastIndex);
        params.put("valueOutputId", vId);
        params.put("indexOutputId", kId);
        params.put("prime", prime);
        params.put("threshold", threshold);
        params.put("rSecretId", rSecretId);
        params.put("storageTag", storageTag);
        return params;
    }
}
