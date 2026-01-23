package utils.crypto.secretsharing;

/**
 * Helper class providing finite field arithmetic operations for Shamir's Secret Sharing.
 * All operations are performed modulo a prime number using long arithmetic.
 */
public class FieldArithmetic {
    
    /**
     * Computes (a + b) mod prime.
     * 
     * @param a First operand
     * @param b Second operand
     * @param prime The prime modulus
     * @return (a + b) mod prime
     */
    public static long modAdd(long a, long b, long prime) {
        return (a + b) % prime;
    }
    
    /**
     * Computes (a * b) mod prime, handling potential overflow.
     * 
     * @param a First operand
     * @param b Second operand
     * @param prime The prime modulus
     * @return (a * b) mod prime
     */
    public static long modMultiply(long a, long b, long prime) {
        // Normalize inputs to [0, prime)
        a = ((a % prime) + prime) % prime;
        b = ((b % prime) + prime) % prime;
        
        // For small enough values, direct multiplication is safe
        if (a < Integer.MAX_VALUE && b < Integer.MAX_VALUE) {
            return (a * b) % prime;
        }
        
        // For larger values, use repeated addition to avoid overflow
        long result = 0;
        a = a % prime;
        while (b > 0) {
            if ((b & 1) == 1) {
                result = (result + a) % prime;
            }
            a = (a * 2) % prime;
            b >>= 1;
        }
        return result;
    }
    
    /**
     * Computes the modular inverse of a number using the Extended Euclidean Algorithm.
     * Finds x such that (a * x) ≡ 1 (mod prime).
     * 
     * @param a The number to invert
     * @param prime The prime modulus
     * @return The modular inverse of a modulo prime
     * @throws ArithmeticException if a is not invertible (i.e., gcd(a, prime) != 1)
     */
    public static long modInverse(long a, long prime) {
        // Normalize a to [0, prime)
        a = ((a % prime) + prime) % prime;
        
        // Extended Euclidean Algorithm
        long t = 0;
        long newT = 1;
        long r = prime;
        long newR = a;
        
        while (newR != 0) {
            long quotient = r / newR;
            
            long tempT = t;
            t = newT;
            newT = tempT - quotient * newT;
            
            long tempR = r;
            r = newR;
            newR = tempR - quotient * newR;
        }
        
        if (r > 1) {
            throw new ArithmeticException("Value " + a + " is not invertible modulo " + prime);
        }
        if (t < 0) {
            t = t + prime;
        }
        
        return t;
    }
}
