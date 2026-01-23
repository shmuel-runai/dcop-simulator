# Shamir Secret Sharing Utilities

A from-scratch implementation of Shamir's Secret Sharing scheme for secure secret distribution and reconstruction.

## Overview

Shamir's Secret Sharing is a cryptographic algorithm that allows a secret to be divided into multiple shares, where a minimum threshold of shares (t) is required to reconstruct the original secret. Any subset of shares smaller than the threshold reveals no information about the secret.

**Key Properties:**
- **Threshold-based**: Requires exactly t shares to reconstruct (t-of-n scheme)
- **Information-theoretic security**: Fewer than t shares reveal nothing about the secret
- **Polynomial-based**: Uses polynomial interpolation over a finite field

## Package Structure

```
utils/crypto/secretsharing/
├── Share.java                  # Share data class
├── ShareGenerator.java         # Single secret share generation
├── BatchShareGenerator.java    # Multiple secrets share generation
├── SecretReconstructor.java    # Secret reconstruction utility
├── IShareStorage.java          # Interface for agent share storage
├── ShareStorageManager.java    # Implementation of share storage
├── FieldArithmetic.java        # Finite field operations
├── ShamirDemo.java             # Usage examples
└── README.md                   # This file
```

## Classes

### Share

Represents a single share in the secret sharing scheme.

**Fields:**
- `secret` (long): Original secret (stored for debugging)
- `index` (int): Share identifier/x-coordinate (must be positive)
- `value` (long): Computed share value/y-coordinate

### ShareGenerator

Main class for creating shares from a single secret.

**Constructor:**
```java
ShareGenerator(long secret, int t, long prime, Random random)
```

**Parameters:**
- `secret`: The secret value to share (must be < prime)
- `t`: Threshold - minimum shares needed to reconstruct
- `prime`: Prime modulus for finite field operations (must be > secret)
- `random`: Random instance for polynomial generation (reusable across multiple generators)

**Methods:**
- `Share generateShare(int index)`: Generate a share for the given index
- `int getThreshold()`: Get the threshold value
- `long getPrime()`: Get the prime modulus
- `long getSecret()`: Get the secret value

### BatchShareGenerator

Generates shares for multiple secrets simultaneously. Useful when you want to share multiple values with the same participants.

**Constructor:**
```java
BatchShareGenerator(long[] secrets, int t, long prime, Random random)
```

**Parameters:**
- `secrets`: Array of secrets to share
- `t`: Threshold for all secrets
- `prime`: Prime modulus for all secrets
- `random`: Random instance for polynomial generation

**Methods:**
- `Share[] generateShares(int index)`: Generate shares at given index for ALL secrets
- `int getSecretCount()`: Get the number of secrets
- `int getThreshold()`: Get the threshold value
- `long getPrime()`: Get the prime modulus

### SecretReconstructor

Utility class for reconstructing secrets from shares.

**Methods:**
- `static long reconstructSecret(List<Share> shares, long prime)`: Reconstruct secret from shares

### FieldArithmetic

Helper class providing finite field arithmetic operations.

**Methods:**
- `static long modInverse(long a, long prime)`: Compute modular inverse
- `static long lagrangeInterpolate(List<Share> shares, long prime)`: Lagrange interpolation at x=0

### IShareStorage

Interface for agents to store and manage shares.

Supports two types of shares:
- **Tagged shares**: Associated with a tag (e.g., round identifier) and can be cleared by tag
- **Sticky shares**: Permanent shares that persist until explicitly cleared

**Methods:**
- `void storeShare(String key, Share share, String tag)`: Store a tagged share
- `void storeStickyShare(String key, Share share)`: Store a permanent share
- `Share getShare(String key)`: Retrieve a share by key
- `boolean hasShare(String key)`: Check if share exists
- `int clearByTag(String tag)`: Clear all shares with specific tag (preserves sticky shares)
- `void clearAll()`: Clear all shares including sticky ones
- `int getShareCount()`: Get total number of stored shares

### ShareStorageManager

Concrete implementation of `IShareStorage` that agents can use as a member.

**Additional methods:**
- `int getStickyShareCount()`: Count only sticky shares
- `int getTaggedShareCount()`: Count only tagged shares
- `List<Share> getSharesByTag(String tag)`: Get all shares with specific tag
- `List<Share> getStickyShares()`: Get all sticky shares
- `List<String> getAllKeys()`: Get all storage keys

**Use cases:**
- Round-based protocols: Tag shares by round, clear after each round
- Multi-phase protocols: Keep some shares across phases (sticky), clear others
- Agent state management: Store received shares from other agents

## Usage

### Basic Example: Single Secret

```java
import utils.crypto.secretsharing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Setup
long secret = 12345L;
int threshold = 3;  // Need 3 shares to reconstruct
long prime = 15485863L;  // A prime > secret
Random rng = new Random(42L);  // Reusable Random instance

// Create share generator
ShareGenerator generator = new ShareGenerator(secret, threshold, prime, rng);

// Generate shares (indices 1-5)
List<Share> shares = new ArrayList<>();
for (int i = 1; i <= 5; i++) {
    shares.add(generator.generateShare(i));
}

// Reconstruct with any 3 shares
List<Share> subset = shares.subList(0, 3);
long reconstructed = SecretReconstructor.reconstructSecret(subset, prime);

// Verify
System.out.println("Match: " + (reconstructed == secret));  // true
```

### Advanced Example: Multiple Secrets (Batch)

```java
// Multiple secrets for the same participants
long[] secrets = {100L, 200L, 300L, 400L};
int threshold = 3;
long prime = 15485863L;
Random rng = new Random(123L);

BatchShareGenerator batchGen = new BatchShareGenerator(secrets, threshold, prime, rng);

// Each participant gets shares for ALL secrets
Share[] participant1Shares = batchGen.generateShares(1);  // 4 shares (one per secret)
Share[] participant2Shares = batchGen.generateShares(2);
Share[] participant3Shares = batchGen.generateShares(3);
// ... etc

// Reconstruct each secret using shares from participants 1, 2, 3
for (int i = 0; i < secrets.length; i++) {
    List<Share> sharesForSecret = new ArrayList<>();
    sharesForSecret.add(participant1Shares[i]);
    sharesForSecret.add(participant2Shares[i]);
    sharesForSecret.add(participant3Shares[i]);
    
    long reconstructed = SecretReconstructor.reconstructSecret(sharesForSecret, prime);
    System.out.println("Secret " + i + ": " + reconstructed);
}
```

### Reusing Random for Efficiency

```java
// Create one Random instance
Random rng = new Random(42L);

// Reuse it for multiple generators
ShareGenerator gen1 = new ShareGenerator(100L, 3, prime, rng);
ShareGenerator gen2 = new ShareGenerator(200L, 3, prime, rng);
ShareGenerator gen3 = new ShareGenerator(300L, 3, prime, rng);

// Or use SecureRandom for production
SecureRandom secureRng = new SecureRandom();
ShareGenerator productionGen = new ShareGenerator(secretValue, 5, prime, secureRng);
```

### Agent Share Storage Example

```java
// Create storage manager (e.g., as agent member)
ShareStorageManager storage = new ShareStorageManager();

// Round 1: Store shares with tag "round-1"
Share share1 = generator.generateShare(1);
Share share2 = generator.generateShare(2);
storage.storeShare("alice_share", share1, "round-1");
storage.storeShare("bob_share", share2, "round-1");

// Store a permanent share
Share myShare = generator.generateShare(3);
storage.storeStickyShare("my_permanent_share", myShare);

System.out.println("Total shares: " + storage.getShareCount());  // 3
System.out.println("Sticky shares: " + storage.getStickyShareCount());  // 1

// Round 2: Store different shares
Share share3 = generator2.generateShare(1);
storage.storeShare("charlie_share", share3, "round-2");

// Clear round-1 shares (keeps sticky and round-2 shares)
int cleared = storage.clearByTag("round-1");
System.out.println("Cleared: " + cleared);  // 2
System.out.println("Remaining: " + storage.getShareCount());  // 2 (sticky + round-2)

// Retrieve and use shares
Share retrieved = storage.getShare("my_permanent_share");
if (storage.hasShare("charlie_share")) {
    Share charlie = storage.getShare("charlie_share");
    // Use for reconstruction...
}

// Complete cleanup at end
storage.clearAll();  // Removes everything including sticky shares
```

### Agent Integration Pattern

```java
class MyAgent {
    private final ShareStorageManager shareStorage;
    private final long prime = 15485863L;
    
    public MyAgent() {
        this.shareStorage = new ShareStorageManager();
    }
    
    // Receive share from another agent
    public void receiveShare(String agentName, Share share, String roundId) {
        String key = agentName + "_share";
        shareStorage.storeShare(key, share, roundId);
    }
    
    // Store own share permanently
    public void storeMyShare(Share share) {
        shareStorage.storeStickyShare("my_share", share);
    }
    
    // End of round cleanup
    public void endRound(String roundId) {
        int cleared = shareStorage.clearByTag(roundId);
        System.out.println("Cleared " + cleared + " shares from " + roundId);
    }
    
    // Reconstruct when ready (example with 3 agents)
    public long reconstructSecret() {
        List<Share> shares = new ArrayList<>();
        shares.add(shareStorage.getShare("alice_share"));
        shares.add(shareStorage.getShare("bob_share"));
        shares.add(shareStorage.getShare("my_share"));
        
        return SecretReconstructor.reconstructSecret(shares, prime);
    }
}
```

## Choosing a Prime

The prime modulus must be:
1. **Larger than the secret**: `prime > secret`
2. **Actually prime**: Use known primes
3. **Fit in a long**: Must be less than 2^63 - 1

**Recommended primes (all fit in long):**
- Small secrets: `15485863L` (Sinalgo's default)
- Medium secrets: `2147483647L` (2^31 - 1, Mersenne prime)
- Large secrets: `2305843009213693951L` (2^61 - 1, Mersenne prime)
- Very large: `9223372036854775783L` (largest prime < 2^63)

## Running the Demo

To see working examples:

```bash
cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease
ant compile
/opt/homebrew/opt/java/bin/java -cp binaries/bin:binaries/jdom.jar \
  utils.crypto.secretsharing.ShamirDemo
```

The demo shows:
- Example 1: Single secret sharing with threshold 3-of-5
- Example 2: Batch processing of 4 secrets simultaneously
- Example 3: Larger secret with threshold 5-of-7
- Example 4: ShareStorageManager with tagged and sticky shares

## How It Works

### 1. Share Generation

When you create a `ShareGenerator`, it generates a random polynomial of degree `t-1`:

```
P(x) = secret + a₁x + a₂x² + ... + aₜ₋₁x^(t-1)
```

Where:
- The constant term is the secret: `P(0) = secret`
- Other coefficients are random values mod prime

To generate a share for index `i`, evaluate: `share_i = P(i) mod prime`

### 2. Secret Reconstruction

Given at least `t` shares, use Lagrange interpolation to find `P(0)`:

```
P(0) = Σᵢ yᵢ · ∏ⱼ≠ᵢ (-xⱼ)/(xᵢ-xⱼ) mod prime
```

Where division is computed via modular inverse.

## Implementation Details

- **Polynomial evaluation**: Uses Horner's method for efficiency
- **Modular arithmetic**: All operations performed mod prime using long arithmetic
- **Overflow handling**: Safe modular multiplication to prevent overflow
- **Random generation**: Accepts Random instance for flexibility (including SecureRandom)
- **Lagrange interpolation**: Computed in finite field with modular inverses
- **Batch processing**: Efficient when sharing multiple secrets with same participants

## Design Decisions

### Why Separate ShareGenerator and SecretReconstructor?

- **Share generation is stateful**: Requires polynomial storage
- **Reconstruction is stateless**: Just mathematical computation
- **Better separation of concerns**: Clear responsibility boundaries
- **Flexible usage**: Can reconstruct without ever creating a generator

### Why Accept Random Instead of Seed?

- **Reusability**: Share one Random across many generators (more efficient)
- **Flexibility**: Caller can use `SecureRandom` for cryptographic security
- **Performance**: Avoids creating new Random objects repeatedly
- **Control**: Caller manages the Random lifecycle

## Security Considerations

1. **Use SecureRandom for production**: `new SecureRandom()` instead of `new Random(seed)`
2. **Keep shares separate**: Storing t shares together defeats the purpose
3. **Prime size matters**: Use primes much larger than your secret space
4. **The secret is stored in Share**: For debugging only - remove in production if needed
5. **Don't reuse polynomials**: Create new ShareGenerator for each secret

## Testing

The implementation has been tested with:
- ✓ Small integers (12345)
- ✓ Large integers (987654321)
- ✓ Various thresholds (3-of-5, 5-of-7)
- ✓ Different share subsets
- ✓ Batch processing of multiple secrets
- ✓ Mersenne primes up to 2^61 - 1

## References

- Shamir, Adi (1979). "How to share a secret". Communications of the ACM. 22 (11): 612–613.
- Finite field arithmetic over prime fields
- Lagrange polynomial interpolation
