package utils.crypto.secretsharing;

import java.util.Random;

/**
 * Generates shares for a single secret using Shamir's Secret Sharing scheme.
 * 
 * This class holds a polynomial with the secret as the constant term and generates
 * shares by evaluating the polynomial at different indices.
 */
public class ShareGenerator {
    private final long secret;
    private final int t; // threshold
    private final long prime;
    private final long[] coefficients; // polynomial coefficients
    
    /**
     * Creates a new ShareGenerator.
     * 
     * Generates a random polynomial of degree t-1 with the secret as the constant term:
     * P(x) = secret + a₁x + a₂x² + ... + aₜ₋₁xᵗ⁻¹
     * 
     * @param secret The secret value to be shared
     * @param t The threshold - minimum number of shares needed to reconstruct the secret
     * @param prime The prime modulus for finite field arithmetic (should be larger than secret)
     * @param random Random instance for generating polynomial coefficients
     * @throws IllegalArgumentException if t < 1 or if secret >= prime
     */
    public ShareGenerator(long secret, int t, long prime, Random random) {
        if (t < 1) {
            throw new IllegalArgumentException("Threshold t must be at least 1");
        }
        if (secret >= prime) {
            throw new IllegalArgumentException("Secret must be less than prime");
        }
        if (secret < 0) {
            throw new IllegalArgumentException("Secret must be non-negative");
        }
        if (prime <= 0) {
            throw new IllegalArgumentException("Prime must be positive");
        }
        
        this.secret = secret;
        this.t = t;
        this.prime = prime;
        this.coefficients = new long[t];
        
        // Initialize polynomial coefficients
        coefficients[0] = secret;
        
        // Generate random coefficients for x^1, x^2, ..., x^(t-1)
        for (int i = 1; i < t; i++) {
            coefficients[i] = Math.abs(random.nextLong()) % prime;
        }
    }
    
    /**
     * Generates a share for the given index.
     * 
     * Evaluates the polynomial P(x) at x = index using Horner's method:
     * P(x) = a₀ + x(a₁ + x(a₂ + x(a₃ + ...)))
     * 
     * @param index The index for which to generate a share (should be positive)
     * @return A new Share containing the secret, index, and computed value
     * @throws IllegalArgumentException if index <= 0
     */
    public Share generateShare(int index) {
        if (index <= 0) {
            throw new IllegalArgumentException("Share index must be positive (index > 0)");
        }
        
        long x = index;
        
        // Evaluate polynomial at x using Horner's method
        long value = coefficients[t - 1];
        for (int i = t - 2; i >= 0; i--) {
            value = FieldArithmetic.modAdd(FieldArithmetic.modMultiply(value, x, prime), coefficients[i], prime);
        }
        
        return new Share(secret, index, value);
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
    
    /**
     * Gets the secret.
     */
    public long getSecret() {
        return secret;
    }
}
