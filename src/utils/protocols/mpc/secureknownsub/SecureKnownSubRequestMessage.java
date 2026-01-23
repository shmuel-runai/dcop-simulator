package utils.protocols.mpc.secureknownsub;

import utils.protocols.core.IProtocolMessage;


/**
 * Request message for Secure Known-Value Subtraction protocol.
 * 
 * Initiates a subtraction where one operand is a known (public) integer value
 * and the other operand is a shared secret.
 */
public class SecureKnownSubRequestMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    private final long knownValue;
    private final String secretId;
    private final String resultId;
    private final boolean knownIsLeft;
    private final long prime;
    private final String resultTag;  // null for sticky, non-null for tagged storage
    
    /**
     * Creates a new SecureKnownSubRequestMessage.
     * 
     * @param protocolId Unique protocol identifier
     * @param senderId The sender/initiator ID
     * @param knownValue The known (public) integer value
     * @param secretId The identifier of the secret share operand
     * @param resultId The identifier for the result share
     * @param knownIsLeft If true, computes (known - secret); if false, computes (secret - known)
     * @param prime The prime modulus
     * @param resultTag Tag for result storage (null for sticky)
     */
    public SecureKnownSubRequestMessage(String protocolId, int senderId, long knownValue, 
                                       String secretId, String resultId, boolean knownIsLeft, 
                                       long prime, String resultTag) {
        this.protocolId = protocolId;
        this.senderId = senderId;
        this.knownValue = knownValue;
        this.secretId = secretId;
        this.resultId = resultId;
        this.knownIsLeft = knownIsLeft;
        this.prime = prime;
        this.resultTag = resultTag;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureKnownSubProtocol.PROTOCOL_TYPE;
    }
    
    @Override
    public int getSenderId() {
        return senderId;
    }
    
    public long getKnownValue() {
        return knownValue;
    }
    
    public String getSecretId() {
        return secretId;
    }
    
    public String getResultId() {
        return resultId;
    }
    
    public boolean isKnownIsLeft() {
        return knownIsLeft;
    }
    
    public long getPrime() {
        return prime;
    }
    
    public String getResultTag() {
        return resultTag;
    }
}

