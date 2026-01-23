package utils.crypto.secretsharing;

import java.util.Random;

/**
 * Generates shares for multiple secrets simultaneously.
 * All secrets use the same threshold and prime, and shares are generated
 * at the same indices for all secrets.
 */
public class BatchShareGenerator {
    private final int t; // threshold
    private final long prime;
    private final ShareGenerator[] generators;
    
    /**
     * Creates a BatchShareGenerator for multiple secrets.
     * 
     * @param secrets Array of secrets to be shared
     * @param t The threshold - minimum number of shares needed to reconstruct each secret
     * @param prime The prime modulus for finite field arithmetic
     * @param random Random instance for generating polynomial coefficients
     * @throws IllegalArgumentException if any validation fails
     */
    public BatchShareGenerator(long[] secrets, int t, long prime, Random random) {
        if (secrets == null || secrets.length == 0) {
            throw new IllegalArgumentException("Secrets array cannot be null or empty");
        }
        
        this.t = t;
        this.prime = prime;
        this.generators = new ShareGenerator[secrets.length];
        
        // Create a generator for each secret
        for (int i = 0; i < secrets.length; i++) {
            generators[i] = new ShareGenerator(secrets[i], t, prime, random);
        }
    }
    
    /**
     * Generates shares at the given index for all secrets.
     * 
     * @param index The index for which to generate shares
     * @return Array of shares, one for each secret
     */
    public Share[] generateShares(int index) {
        Share[] shares = new Share[generators.length];
        for (int i = 0; i < generators.length; i++) {
            shares[i] = generators[i].generateShare(index);
        }
        return shares;
    }
    
    /**
     * Gets the number of secrets being shared.
     */
    public int getSecretCount() {
        return generators.length;
    }
    
    /**
     * Gets the threshold value.
     */
    public int getThreshold() {
        return t;
    }
    
    /**
     * Gets the prime modulus.
     */
    public long getPrime() {
        return prime;
    }
}
