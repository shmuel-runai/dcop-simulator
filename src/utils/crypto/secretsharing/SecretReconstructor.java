package utils.crypto.secretsharing;

import java.util.List;

/**
 * Utility class for reconstructing secrets from shares using Shamir's Secret Sharing.
 */
public class SecretReconstructor {
    
    /**
     * Reconstructs the secret from a list of shares using Lagrange interpolation.
     * 
     * Requires at least t shares to successfully reconstruct the secret, where t
     * is the threshold that was used when creating the shares.
     * 
     * @param shares List of shares to use for reconstruction (must contain at least t shares)
     * @param prime The prime modulus that was used when creating the shares
     * @return The reconstructed secret value
     * @throws IllegalArgumentException if shares list is null or empty
     */
    public static long reconstructSecret(List<Share> shares, long prime) {
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("Shares list cannot be null or empty");
        }
        
        return lagrangeInterpolate(shares, prime);
    }
    
    /**
     * Performs Lagrange interpolation to reconstruct the secret (polynomial value at x=0).
     * 
     * Formula: P(0) = Σᵢ yᵢ · ∏ⱼ≠ᵢ (0-xⱼ)/(xᵢ-xⱼ)
     * Simplified: P(0) = Σᵢ yᵢ · ∏ⱼ≠ᵢ (-xⱼ)/(xᵢ-xⱼ)
     * 
     * All arithmetic is performed in the finite field modulo prime.
     * 
     * @param shares List of shares to use for interpolation
     * @param prime The prime modulus for the finite field
     * @return The reconstructed secret (polynomial evaluated at x=0)
     */
    private static long lagrangeInterpolate(List<Share> shares, long prime) {
        long secret = 0;
        
        // For each share i
        for (int i = 0; i < shares.size(); i++) {
            Share shareI = shares.get(i);
            long xi = shareI.getIndex();
            long yi = shareI.getValue();
            
            // Calculate the Lagrange basis polynomial at x=0
            long numerator = 1;
            long denominator = 1;
            
            // For each share j != i
            for (int j = 0; j < shares.size(); j++) {
                if (i != j) {
                    Share shareJ = shares.get(j);
                    long xj = shareJ.getIndex();
                    
                    // Numerator: multiply by (0 - xj) = -xj
                    numerator = FieldArithmetic.modMultiply(numerator, -xj, prime);
                    
                    // Denominator: multiply by (xi - xj)
                    denominator = FieldArithmetic.modMultiply(denominator, xi - xj, prime);
                }
            }
            
            // Compute yi * (numerator / denominator) in the finite field
            // Division is done via modular inverse: a/b = a * b^(-1) mod prime
            long lagrangeBasis = FieldArithmetic.modMultiply(numerator, FieldArithmetic.modInverse(denominator, prime), prime);
            long term = FieldArithmetic.modMultiply(yi, lagrangeBasis, prime);
            
            // Add to the running sum
            secret = FieldArithmetic.modAdd(secret, term, prime);
        }
        
        return secret;
    }
}









