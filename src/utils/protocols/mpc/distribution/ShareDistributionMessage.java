package utils.protocols.mpc.distribution;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;

import utils.crypto.secretsharing.Share;

/**
 * Message containing a share for distribution.
 * 
 * Sent by the initiator to each participant with their specific share.
 * Each agent receives their unique share for the secret.
 * 
 * This is a framework-agnostic POJO that implements IProtocolMessage.
 */
public class ShareDistributionMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final String protocolType;
    private final int senderId;
    
    /**
     * The identifier for the secret being shared.
     */
    public final String secretId;
    
    /**
     * The share being distributed to the recipient.
     */
    public final Share share;
    
    /**
     * The threshold value (k) - minimum shares needed to reconstruct.
     */
    public final int threshold;
    
    /**
     * The prime used for field arithmetic.
     */
    public final long prime;
    
    /**
     * Storage scope tag (null = sticky/permanent).
     */
    private final String storageTag;
    
    public ShareDistributionMessage(String protocolId, int senderId, 
                                   String secretId, Share share, 
                                   int threshold, long prime, String storageTag) {
        this.protocolId = protocolId;
        this.protocolType = ShareDistributionProtocol.PROTOCOL_TYPE;
        this.senderId = senderId;
        this.secretId = secretId;
        this.share = share;
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
        return protocolType;
    }
    
    public String getMessageType() {
        return "SHARE_DISTRIBUTION";
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
        params.put("threshold", Integer.valueOf(threshold));
        params.put("initiatorId", Integer.valueOf(senderId));
        params.put("storageTag", storageTag);
        return params;
    }
    
    // Getters for encapsulation
    public String getSecretId() { return secretId; }
    public Share getShare() { return share; }
    public int getThreshold() { return threshold; }
    public long getPrime() { return prime; }
    public String getStorageTag() { return storageTag; }
    
    @Override
    public String toString() {
        return String.format("ShareDistribution[pid=%s, from=%d, secretId=%s, share=%s, k=%d]",
                           protocolId, senderId, secretId, share, threshold);
    }
}

