package utils.protocols.testing;

import utils.protocols.core.*;
import utils.protocols.mpc.distribution.ShareDistributionProtocol;
import utils.protocols.mpc.secureadd.SecureAddProtocol;
import utils.protocols.mpc.securesub.SecureSubProtocol;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.protocols.mpc.securecompare.SecureCompareProtocol;
import utils.protocols.mpc.securemin.SecureMinProtocol;
import utils.protocols.mpc.secureinvert.SecureInvertProtocol;
import utils.protocols.mpc.secureiszero.SecureIsZeroProtocol;
import utils.protocols.mpc.secureknownsub.SecureKnownSubProtocol;
import utils.protocols.mpc.reconstruct.ReconstructSecretProtocol;
import utils.protocols.mpc.secureadd.ISecureAddListener;
import utils.protocols.mpc.securesub.ISecureSubListener;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securecompare.ISecureCompareListener;
import utils.protocols.mpc.securemin.ISecureMinListener;
import utils.protocols.mpc.secureinvert.ISecureInvertListener;
import utils.protocols.mpc.secureiszero.ISecureIsZeroListener;
import utils.protocols.mpc.secureknownsub.ISecureKnownSubListener;
import utils.protocols.mpc.reconstruct.IReconstructListener;
import utils.protocols.mpc.distribution.IShareDistributionListener;
import utils.crypto.secretsharing.IShareStorage;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Meta-protocol that orchestrates a single MPC test iteration.
 * 
 * Demonstrates protocol composition by calling other protocols:
 * 1. Distributes three secrets (a, b, r-secret)
 * 2. Computes SecureAdd, SecureSub, SecureMultiply, SecureCompare, SecureMin, SecureInvert, and SecureIsZero
 * 3. Reconstructs all eight results in parallel
 * 4. Verifies results and reports pass/fail
 * 
 * This protocol proves that protocols can orchestrate other protocols.
 */
public class MPCSingleTestProtocol implements IDistributedProtocol,
        IShareDistributionListener, ISecureAddListener, ISecureSubListener,
        ISecureMultiplyListener, ISecureCompareListener, ISecureMinListener, 
        ISecureInvertListener, ISecureIsZeroListener, ISecureKnownSubListener,
        IReconstructListener {
    
    public static final String PROTOCOL_TYPE = "MPC_SINGLE_TEST";
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first (cascades to their dependencies)
        ShareDistributionProtocol.registerFactory(manager);
        SecureAddProtocol.registerFactory(manager);
        SecureSubProtocol.registerFactory(manager);
        SecureMultiplyProtocol.registerFactory(manager);
        SecureCompareProtocol.registerFactory(manager);
        SecureMinProtocol.registerFactory(manager);
        SecureInvertProtocol.registerFactory(manager);
        SecureIsZeroProtocol.registerFactory(manager);
        SecureKnownSubProtocol.registerFactory(manager);
        ReconstructSecretProtocol.registerFactory(manager);
        
        // Initiator only
        manager.registerProtocolFactory(PROTOCOL_TYPE, () -> new MPCSingleTestProtocol(), null);
    }
    
    private String protocolId;
    private int agentId;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    private long prime;
    private int threshold;
    private int s; // Exponent for prime = 2^s - 1
    private IMPCSingleTestListener listener;
    private int testNumber;
    
    // Generated values for this test
    private long secretA;
    private long secretB;
    private long rSecret;
    
    // Test state
    private enum TestPhase {
        DISTRIBUTING,
        COMPUTING,
        RECONSTRUCTING,
        VERIFYING
    }
    
    private TestPhase currentPhase;
    private boolean complete;
    private boolean successful;
    
    // Parallel operation tracking
    private Set<String> completedDistributions;
    private Set<String> completedComputations;
    private Set<String> completedReconstructions;
    
    // Reconstructed values for verification
    private long reconstructedSum;
    private long reconstructedDifference;
    private long reconstructedProduct;
    private long reconstructedComparison;
    private long reconstructedMin;
    private long reconstructedInverted;
    private long reconstructedIsZeroOfCmp;
    private long reconstructedIsZeroOfInverted;
    private long reconstructedKnownSub1;  // a - 100
    private long reconstructedKnownSub2;  // 200 - b
    
    // Unique secret IDs for this test iteration
    private String secretAId;
    private String secretBId;
    private String rSecretId;
    private String resCId;
    private String diffId;
    private String secretDId;
    private String cmpResultId;
    private String minResultId;
    private String invertedId;
    private String isZeroOfCmpId;
    private String isZeroOfInvertedId;
    private String knownSub1Id;  // a - 100
    private String knownSub2Id;  // 200 - b
    
    /**
     * Creates a new MPCSingleTestProtocol instance.
     */
    public MPCSingleTestProtocol() {
        this.completedDistributions = new HashSet<>();
        this.completedComputations = new HashSet<>();
        this.completedReconstructions = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract parameters
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.shareStorage = (IShareStorage) params.get("shareStorage");
        this.participants = (List<Integer>) params.get("participants");
        this.prime = (Long) params.get("prime");
        this.threshold = (Integer) params.get("threshold");
        this.s = (Integer) params.get("s");
        this.listener = (IMPCSingleTestListener) params.get("listener");
        this.testNumber = (Integer) params.get("testNumber");
        
        // Generate unique IDs for this test iteration
        this.secretAId = "test-" + testNumber + "-a";
        this.secretBId = "test-" + testNumber + "-b";
        this.rSecretId = "test-" + testNumber + "-r";
        this.resCId = "test-" + testNumber + "-c";
        this.diffId = "test-" + testNumber + "-diff";
        this.secretDId = "test-" + testNumber + "-d";
        this.cmpResultId = "test-" + testNumber + "-cmp";
        this.minResultId = "test-" + testNumber + "-min";
        this.invertedId = "test-" + testNumber + "-inverted";
        this.isZeroOfCmpId = "test-" + testNumber + "-iszero-cmp";
        this.isZeroOfInvertedId = "test-" + testNumber + "-iszero-inv";
        this.knownSub1Id = "test-" + testNumber + "-knownsub1";
        this.knownSub2Id = "test-" + testNumber + "-knownsub2";
        
        // Start the test by distributing secrets
        startDistributionPhase();
    }
    
    /**
     * Phase 1: Generate random values and distribute them.
     */
    private void startDistributionPhase() {
        currentPhase = TestPhase.DISTRIBUTING;
        completedDistributions.clear();
        
        // Generate random values
        Random rng = new Random();
        secretA = Math.abs(rng.nextLong() % (prime / 2));
        secretB = Math.abs(rng.nextLong() % (prime / 2));
        rSecret = Math.abs(rng.nextLong() % (prime / 2));
        
        System.out.println("Test " + testNumber + ": Starting distribution phase");
        System.out.println("  a=" + secretA + ", b=" + secretB + ", r=" + rSecret);
        
        // Distribute all 3 secrets in parallel
        ShareDistributionProtocol.start(
            manager, secretA, secretAId, threshold, prime,
            participants, shareStorage, null, this  // null = sticky (tests don't need cleanup)
        );
        
        ShareDistributionProtocol.start(
            manager, secretB, secretBId, threshold, prime,
            participants, shareStorage, null, this
        );
        
        ShareDistributionProtocol.start(
            manager, rSecret, rSecretId, threshold, prime,
            participants, shareStorage, null, this
        );
    }
    
    @Override
    public void onShareDistributionComplete(String protocolId, String secretId) {
        if (currentPhase != TestPhase.DISTRIBUTING) return;
        
        completedDistributions.add(secretId);
        
        System.out.println("Test " + testNumber + ": Distribution complete for " + secretId + 
                         " (" + completedDistributions.size() + "/3)");
        
        // When all 3 distributions are complete, move to computation
        if (completedDistributions.size() == 3) {
            startComputationPhase();
        }
    }
    
    /**
     * Phase 2: Compute SecureAdd, SecureSub, SecureMultiply, SecureCompare, SecureMin, SecureInvert, and SecureIsZero.
     * Note: SecureInvert starts after SecureCompare completes (dependency).
     *       SecureIsZero tests start after SecureInvert completes (dependency).
     */
    private void startComputationPhase() {
        currentPhase = TestPhase.COMPUTING;
        completedComputations.clear();
        
        System.out.println("Test " + testNumber + ": Starting computation phase (add, sub, multiply, compare, min, invert, isZero, and knownSub)");
        
        // Start SecureAdd (now uses NEW Initiator/Responder pattern)
        SecureAddProtocol.start(
            manager, secretAId, secretBId, resCId, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start SecureSub in parallel
        SecureSubProtocol.start(
            manager, secretAId, secretBId, diffId, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start SecureKnownSub operations in parallel (testing known-value subtraction)
        // Test 1: a - 100 (secret - known)
        SecureKnownSubProtocol.start(
            manager, 100L, secretAId, knownSub1Id, false, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Test 2: 200 - b (known - secret)
        SecureKnownSubProtocol.start(
            manager, 200L, secretBId, knownSub2Id, true, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start SecureMultiply in parallel
        SecureMultiplyProtocol.start(
            manager, secretAId, secretBId, secretDId, rSecretId, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start SecureCompare in parallel (compare a < b)
        // Note: Uses pre-distributed "r-key" and its bits for LSB extraction
        SecureCompareProtocol.start(
            manager, secretAId, secretBId, cmpResultId, "r-key", "r-key",
            prime, threshold, participants, shareStorage, null, this
        );
        
        // Start SecureMin (meta-protocol that orchestrates sub, compare, multiply, add)
        SecureMinProtocol.start(
            manager, secretAId, secretBId, minResultId, rSecretId, prime, threshold,
            participants, shareStorage, null, this  // null = sticky (tests don't need cleanup)
        );
    }
    
    @Override
    public void onSecureAddComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("add");
        
        System.out.println("Test " + testNumber + ": Add complete (" + 
                         completedComputations.size() + "/10)");
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureSubComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("sub");
        
        System.out.println("Test " + testNumber + ": Sub complete (" + 
                         completedComputations.size() + "/10)");
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureKnownSubComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        if (resultSecretId.equals(knownSub1Id)) {
            completedComputations.add("knownsub1");
            System.out.println("Test " + testNumber + ": KnownSub1 (a-100) complete (" + 
                             completedComputations.size() + "/10)");
        } else if (resultSecretId.equals(knownSub2Id)) {
            completedComputations.add("knownsub2");
            System.out.println("Test " + testNumber + ": KnownSub2 (200-b) complete (" + 
                             completedComputations.size() + "/10)");
        }
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureMultiplyComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("multiply");
        
        System.out.println("Test " + testNumber + ": Multiply complete (" + 
                         completedComputations.size() + "/10)");
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureCompareComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("compare");
        
        System.out.println("Test " + testNumber + ": Compare complete (" + 
                         completedComputations.size() + "/10)");
        
        // Now that comparison is complete, we can invert it
        // Start SecureInvert on the comparison result (1 - cmp)
        SecureInvertProtocol.start(
            manager, cmpResultId, invertedId, prime,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureMinComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("min");
        
        System.out.println("Test " + testNumber + ": Min complete (" + 
                         completedComputations.size() + "/10)");
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureInvertComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedComputations.add("invert");
        
        System.out.println("Test " + testNumber + ": Invert complete (" + 
                         completedComputations.size() + "/10)");
        
        // Now that inversion is complete, we can test if compare and inverted are zero
        // Start IsZero on the comparison result (using Fermat's Little Theorem: 1 - x^(p-1))
        SecureIsZeroProtocol.start(
            manager, cmpResultId, isZeroOfCmpId, rSecretId, prime, s,
            participants, shareStorage, null, this  // null = sticky (tests don't need cleanup)
        );
        
        // Start IsZero on the inverted result in parallel
        SecureIsZeroProtocol.start(
            manager, invertedId, isZeroOfInvertedId, rSecretId, prime, s,
            participants, shareStorage, null, this
        );
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    @Override
    public void onSecureIsZeroComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        // Track which isZero completed
        if (resultSecretId.equals(isZeroOfCmpId)) {
            completedComputations.add("iszero-cmp");
            System.out.println("Test " + testNumber + ": IsZero(compare) complete (" + 
                             completedComputations.size() + "/10)");
        } else if (resultSecretId.equals(isZeroOfInvertedId)) {
            completedComputations.add("iszero-inv");
            System.out.println("Test " + testNumber + ": IsZero(inverted) complete (" + 
                             completedComputations.size() + "/10)");
        }
        
        // When all ten computations are complete, move to reconstruction
        if (completedComputations.size() == 10) {
            startReconstructionPhase();
        }
    }
    
    /**
     * Phase 3: Reconstruct all eight results in parallel.
     */
    private void startReconstructionPhase() {
        currentPhase = TestPhase.RECONSTRUCTING;
        completedReconstructions.clear();
        
        System.out.println("Test " + testNumber + ": Starting reconstruction phase (all ten results in parallel)");
        
        // Reconstruct sum
        ReconstructSecretProtocol.start(
            manager, resCId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct difference in parallel
        ReconstructSecretProtocol.start(
            manager, diffId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct product in parallel
        ReconstructSecretProtocol.start(
            manager, secretDId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct comparison result in parallel
        ReconstructSecretProtocol.start(
            manager, cmpResultId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct min result in parallel
        ReconstructSecretProtocol.start(
            manager, minResultId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct inverted result in parallel
        ReconstructSecretProtocol.start(
            manager, invertedId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct isZero(compare) result in parallel
        ReconstructSecretProtocol.start(
            manager, isZeroOfCmpId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct isZero(inverted) result in parallel
        ReconstructSecretProtocol.start(
            manager, isZeroOfInvertedId, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct knownSub1 (a - 100) result in parallel
        ReconstructSecretProtocol.start(
            manager, knownSub1Id, prime,
            participants, shareStorage, this
        );
        
        // Reconstruct knownSub2 (200 - b) result in parallel
        ReconstructSecretProtocol.start(
            manager, knownSub2Id, prime,
            participants, shareStorage, this
        );
    }
    
    @Override
    public void onReconstructComplete(String protocolId, String secretId, long reconstructedValue) {
        if (currentPhase != TestPhase.RECONSTRUCTING) return;
        
        // Store the reconstructed value
        if (secretId.equals(resCId)) {
            reconstructedSum = reconstructedValue;
            completedReconstructions.add("sum");
            System.out.println("Test " + testNumber + ": Sum reconstructed = " + reconstructedSum);
        } else if (secretId.equals(diffId)) {
            reconstructedDifference = reconstructedValue;
            completedReconstructions.add("difference");
            System.out.println("Test " + testNumber + ": Difference reconstructed = " + reconstructedDifference);
        } else if (secretId.equals(secretDId)) {
            reconstructedProduct = reconstructedValue;
            completedReconstructions.add("product");
            System.out.println("Test " + testNumber + ": Product reconstructed = " + reconstructedProduct);
        } else if (secretId.equals(cmpResultId)) {
            reconstructedComparison = reconstructedValue;
            completedReconstructions.add("comparison");
            System.out.println("Test " + testNumber + ": Comparison reconstructed = " + reconstructedComparison + 
                             " (a=" + secretA + " < b=" + secretB + " ? " + (reconstructedComparison == 1 ? "YES" : "NO") + ")");
        } else if (secretId.equals(minResultId)) {
            reconstructedMin = reconstructedValue;
            completedReconstructions.add("min");
            System.out.println("Test " + testNumber + ": Min reconstructed = " + reconstructedMin + 
                             " (min(" + secretA + ", " + secretB + "))");
        } else if (secretId.equals(invertedId)) {
            reconstructedInverted = reconstructedValue;
            completedReconstructions.add("inverted");
            System.out.println("Test " + testNumber + ": Inverted reconstructed = " + reconstructedInverted + 
                             " (1 - " + reconstructedComparison + ")");
        } else if (secretId.equals(isZeroOfCmpId)) {
            reconstructedIsZeroOfCmp = reconstructedValue;
            completedReconstructions.add("iszero-cmp");
            System.out.println("Test " + testNumber + ": IsZero(compare) reconstructed = " + reconstructedIsZeroOfCmp + 
                             " (isZero(" + reconstructedComparison + "))");
        } else if (secretId.equals(isZeroOfInvertedId)) {
            reconstructedIsZeroOfInverted = reconstructedValue;
            completedReconstructions.add("iszero-inv");
            System.out.println("Test " + testNumber + ": IsZero(inverted) reconstructed = " + reconstructedIsZeroOfInverted + 
                             " (isZero(" + reconstructedInverted + "))");
        } else if (secretId.equals(knownSub1Id)) {
            reconstructedKnownSub1 = reconstructedValue;
            completedReconstructions.add("knownsub1");
            System.out.println("Test " + testNumber + ": KnownSub1 reconstructed = " + reconstructedKnownSub1 + 
                             " (a - 100 = " + secretA + " - 100)");
        } else if (secretId.equals(knownSub2Id)) {
            reconstructedKnownSub2 = reconstructedValue;
            completedReconstructions.add("knownsub2");
            System.out.println("Test " + testNumber + ": KnownSub2 reconstructed = " + reconstructedKnownSub2 + 
                             " (200 - b = 200 - " + secretB + ")");
        }
        
        // When all ten reconstructions are complete, verify and complete
        if (completedReconstructions.size() == 10) {
            verifyAndComplete();
        }
    }
    
    /**
     * Phase 4: Verify results and complete the test.
     */
    private void verifyAndComplete() {
        currentPhase = TestPhase.VERIFYING;
        
        long expectedSum = (secretA + secretB) % prime;
        long expectedDifference = (secretA - secretB) % prime;
        if (expectedDifference < 0) {
            expectedDifference += prime;
        }
        long expectedProduct = (secretA * secretB) % prime;
        long expectedComparison = (secretA < secretB) ? 1L : 0L;
        long expectedMin = Math.min(secretA, secretB);
        long expectedInverted = (1 - expectedComparison) % prime;
        if (expectedInverted < 0) {
            expectedInverted += prime;
        }
        long expectedIsZeroOfCmp = (expectedComparison == 0) ? 1L : 0L;
        long expectedIsZeroOfInverted = (expectedInverted == 0) ? 1L : 0L;
        long expectedKnownSub1 = (secretA - 100) % prime;
        if (expectedKnownSub1 < 0) {
            expectedKnownSub1 += prime;
        }
        long expectedKnownSub2 = (200 - secretB) % prime;
        if (expectedKnownSub2 < 0) {
            expectedKnownSub2 += prime;
        }
        
        boolean sumCorrect = (reconstructedSum == expectedSum);
        boolean differenceCorrect = (reconstructedDifference == expectedDifference);
        boolean productCorrect = (reconstructedProduct == expectedProduct);
        boolean comparisonCorrect = (reconstructedComparison == expectedComparison);
        boolean minCorrect = (reconstructedMin == expectedMin);
        boolean invertedCorrect = (reconstructedInverted == expectedInverted);
        boolean isZeroOfCmpCorrect = (reconstructedIsZeroOfCmp == expectedIsZeroOfCmp);
        boolean isZeroOfInvertedCorrect = (reconstructedIsZeroOfInverted == expectedIsZeroOfInverted);
        boolean knownSub1Correct = (reconstructedKnownSub1 == expectedKnownSub1);
        boolean knownSub2Correct = (reconstructedKnownSub2 == expectedKnownSub2);
        boolean testPassed = sumCorrect && differenceCorrect && productCorrect && comparisonCorrect && 
                            minCorrect && invertedCorrect && isZeroOfCmpCorrect && isZeroOfInvertedCorrect &&
                            knownSub1Correct && knownSub2Correct;
        
        System.out.println("Test " + testNumber + ": Verification phase");
        System.out.println("  a=" + secretA + ", b=" + secretB);
        System.out.println("  Expected sum (a+b): " + expectedSum + ", got: " + reconstructedSum + 
                         " [" + (sumCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected difference (a-b): " + expectedDifference + ", got: " + reconstructedDifference + 
                         " [" + (differenceCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected product (a*b): " + expectedProduct + ", got: " + reconstructedProduct + 
                         " [" + (productCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected comparison (a<b): " + expectedComparison + ", got: " + reconstructedComparison + 
                         " [" + (comparisonCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected min(a,b): " + expectedMin + ", got: " + reconstructedMin + 
                         " [" + (minCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected invert(cmp): " + expectedInverted + ", got: " + reconstructedInverted + 
                         " [" + (invertedCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected isZero(cmp): " + expectedIsZeroOfCmp + ", got: " + reconstructedIsZeroOfCmp + 
                         " [" + (isZeroOfCmpCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected isZero(inv): " + expectedIsZeroOfInverted + ", got: " + reconstructedIsZeroOfInverted + 
                         " [" + (isZeroOfInvertedCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected knownSub1 (a-100): " + expectedKnownSub1 + ", got: " + reconstructedKnownSub1 + 
                         " [" + (knownSub1Correct ? "✓" : "✗") + "]");
        System.out.println("  Expected knownSub2 (200-b): " + expectedKnownSub2 + ", got: " + reconstructedKnownSub2 + 
                         " [" + (knownSub2Correct ? "✓" : "✗") + "]");
        System.out.println("  Overall: " + (testPassed ? "✓ PASSED" : "✗ FAILED"));
        
        // Mark protocol complete
        complete = true;
        successful = testPassed;
        
        // Notify listener
        if (listener != null) {
            listener.onTestComplete(this.protocolId, testNumber, testPassed);
        }
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // This meta-protocol doesn't receive any direct messages
        // It only orchestrates other protocols through their listeners
    }
    
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    @Override
    public boolean isSuccessful() {
        return successful;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return TestingConstants.MPC_SINGLE_TEST;
    }
    
    @Override
    public Object getResult() {
        return successful ? "PASSED" : "FAILED";
    }
}

