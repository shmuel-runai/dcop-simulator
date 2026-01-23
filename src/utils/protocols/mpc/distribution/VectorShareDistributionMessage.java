package utils.protocols.mpc.distribution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;

import utils.crypto.secretsharing.Share;

/**
 * Message for distributing shares of a vector of secrets.
 * 
 * Contains shares for multiple secrets (e.g., E[0], E[1], ..., E[d-1])
 * sent from initiator to a single participant.
 */
public class VectorShareDistributionMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    private final String baseSecretId;  // e.g., "E_5-r3" for E_5-r3[0], E_5-r3[1], ...
    private final List<Share> shares;   // shares[i] is share for baseSecretId[i]
    private final int threshold;
    private final long prime;
    private final String storageTag;    // null = sticky, otherwise scoped to this tag
    
    /**
     * Creates a vector share distribution message.
     * 
     * @param protocolId The protocol ID
     * @param senderId The sender (initiator) ID
     * @param baseSecretId Base secret ID (without index)
     * @param shares List of shares, indexed by position
     * @param threshold The Shamir threshold
     * @param prime The prime modulus
     * @param storageTag Storage scope tag (null = sticky/permanent)
     */
    public VectorShareDistributionMessage(String protocolId, int senderId,
                                          String baseSecretId, List<Share> shares,
                                          int threshold, long prime, String storageTag) {
        this.protocolId = protocolId;
        this.senderId = senderId;
        this.baseSecretId = baseSecretId;
        this.shares = shares;
        this.threshold = threshold;
        this.prime = prime;
        this.storageTag = storageTag;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return VectorShareDistributionProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    public String getBaseSecretId() {
        return baseSecretId;
    }
    
    public List<Share> getShares() {
        return shares;
    }
    
    public int getVectorSize() {
        return shares.size();
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    public long getPrime() {
        return prime;
    }
    
    /**
     * Gets the storage tag (null = sticky/permanent).
     */
    public String getStorageTag() {
        return storageTag;
    }
    
    /**
     * Gets the full secret ID for a given index.
     * 
     * @param index The index (0-based)
     * @return The full secret ID (e.g., "E_5-r3[2]" for index 2)
     */
    public String getSecretId(int index) {
        return baseSecretId + "[" + index + "]";
    }
    
    @Override
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("baseSecretId", baseSecretId);
        params.put("prime", Long.valueOf(prime));
        params.put("threshold", Integer.valueOf(threshold));
        params.put("initiatorId", Integer.valueOf(senderId));
        params.put("storageTag", storageTag);
        return params;
    }
}

