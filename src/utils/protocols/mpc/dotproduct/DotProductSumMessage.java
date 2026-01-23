package utils.protocols.mpc.dotproduct;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;


/**
 * Message broadcast by initiator to tell all agents to compute the local sum.
 * 
 * Sent after all SecureMultiply sub-protocols have completed.
 * Upon receiving this message, each agent sums their shares of the products
 * and stores the result.
 */
public class DotProductSumMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int vectorSize;
    private final String outputId;
    private final String baseIdA;
    private final String baseIdB;
    private final long prime;
    private final String storageTag;
    
    public DotProductSumMessage(String protocolId, int vectorSize, String outputId, 
                                 String baseIdA, String baseIdB, long prime, String storageTag) {
        this.protocolId = protocolId;
        this.vectorSize = vectorSize;
        this.outputId = outputId;
        this.baseIdA = baseIdA;
        this.baseIdB = baseIdB;
        this.prime = prime;
        this.storageTag = storageTag;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureDotProductProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return -1;  // Set by transport layer
    }
    
    public int getVectorSize() {
        return vectorSize;
    }
    
    public String getOutputId() {
        return outputId;
    }
    
    public String getBaseIdA() {
        return baseIdA;
    }
    
    public String getBaseIdB() {
        return baseIdB;
    }
    
    public long getPrime() {
        return prime;
    }
    
    public String getStorageTag() {
        return storageTag;
    }
    
    @Override
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("vectorSize", Integer.valueOf(vectorSize));
        params.put("outputSecretId", outputId);
        params.put("baseIdA", baseIdA);
        params.put("baseIdB", baseIdB);
        params.put("prime", Long.valueOf(prime));
        params.put("storageTag", storageTag);
        params.put("initiatorId", Integer.valueOf(getSenderId()));
        return params;
    }
}
