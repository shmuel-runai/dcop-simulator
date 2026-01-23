package utils.crypto.secretsharing;

/**
 * Represents a share in Shamir's Secret Sharing scheme.
 * Each share contains the original secret (for debugging), an index (x-coordinate),
 * and the computed value (y-coordinate) from evaluating the polynomial at the index.
 */
public class Share {
    private final long secret;
    private final int index;
    private final long value;
    
    /**
     * Constructs a new Share.
     * 
     * @param secret The original secret being shared (stored for debugging)
     * @param index The share's index/identifier (x-coordinate)
     * @param value The computed share value (y-coordinate, polynomial evaluated at index)
     */
    public Share(long secret, int index, long value) {
        this.secret = secret;
        this.index = index;
        this.value = value;
    }
    
    /**
     * Gets the original secret.
     * 
     * @return The secret value
     */
    public long getSecret() {
        return secret;
    }
    
    /**
     * Gets the share's index.
     * 
     * @return The index (x-coordinate)
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * Gets the share's value.
     * 
     * @return The value (y-coordinate)
     */
    public long getValue() {
        return value;
    }
    
    /**
     * Adds another share to this one (modular arithmetic).
     * Returns a new share with the sum of values and secrets.
     * Keeps the index of this share.
     * 
     * @param other The other share to add
     * @param prime The prime modulus
     * @return A new share with (this.value + other.value) mod prime
     */
    public Share modAdd(Share other, long prime) {
        long newValue = FieldArithmetic.modAdd(this.value, other.value, prime);
        long newSecret = FieldArithmetic.modAdd(this.secret, other.secret, prime);
        return new Share(newSecret, this.index, newValue);
    }
    
    /**
     * Subtracts another share from this one (modular arithmetic).
     * Returns a new share with the difference of values and secrets.
     * Keeps the index of this share.
     * 
     * @param other The other share to subtract
     * @param prime The prime modulus
     * @return A new share with (this.value - other.value) mod prime
     */
    public Share modSub(Share other, long prime) {
        long newValue = (this.value - other.value) % prime;
        if (newValue < 0) newValue += prime;
        long newSecret = (this.secret - other.secret) % prime;
        if (newSecret < 0) newSecret += prime;
        return new Share(newSecret, this.index, newValue);
    }
    
    /**
     * Multiplies this share by a known constant (modular arithmetic).
     * This is a local operation - no communication needed.
     * 
     * @param constant The constant to multiply by
     * @param prime The prime modulus
     * @return A new share with (this.value * constant) mod prime
     */
    public Share constMultiply(long constant, long prime) {
        long newValue = (this.value * constant) % prime;
        if (newValue < 0) newValue += prime;
        long newSecret = (this.secret * constant) % prime;
        if (newSecret < 0) newSecret += prime;
        return new Share(newSecret, this.index, newValue);
    }
    
    /**
     * Creates a share representing (1 - this) in the field.
     * Useful for complement operations in secure computation.
     * 
     * @param prime The prime modulus
     * @return A new share with value (1 - this.value) mod prime
     */
    public Share oneMinus(long prime) {
        long newValue = (1 - this.value) % prime;
        if (newValue < 0) newValue += prime;
        long newSecret = (1 - this.secret) % prime;
        if (newSecret < 0) newSecret += prime;
        return new Share(newSecret, this.index, newValue);
    }
    
    @Override
    public String toString() {
        return String.format("Share{index=%d, value=%d, secret=%d}", 
            index, value, secret);
    }
}

