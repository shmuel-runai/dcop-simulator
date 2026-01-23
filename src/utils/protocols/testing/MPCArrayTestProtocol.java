package utils.protocols.testing;

import utils.protocols.core.*;
import utils.protocols.mpc.distribution.ShareDistributionProtocol;
import utils.protocols.mpc.findmin.SecureFindMinProtocol;
import utils.protocols.mpc.findmax.SecureFindMaxProtocol;
import utils.protocols.mpc.dotproduct.SecureDotProductProtocol;
import utils.protocols.mpc.reconstruct.ReconstructSecretProtocol;
import utils.protocols.mpc.distribution.IShareDistributionListener;
import utils.protocols.mpc.findmin.ISecureFindMinListener;
import utils.protocols.mpc.findmax.ISecureFindMaxListener;
import utils.protocols.mpc.dotproduct.ISecureDotProductListener;
import utils.protocols.mpc.reconstruct.IReconstructListener;
import utils.crypto.secretsharing.IShareStorage;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * MPC Array Test Protocol.
 * 
 * Tests array-based MPC operations:
 * - FindMin protocol
 * - FindMax protocol
 * - DotProduct protocol
 * 
 * Generates two random arrays (A and B) of size 2-10, distributes all elements as shares,
 * runs FindMin, FindMax on array A, runs DotProduct on A·B,
 * reconstructs the results, and verifies correctness.
 */
public class MPCArrayTestProtocol implements IDistributedProtocol,
        IShareDistributionListener, ISecureFindMinListener, ISecureFindMaxListener, 
        ISecureDotProductListener, IReconstructListener {
    
    public static final String PROTOCOL_TYPE = "MPC_ARRAY_TEST";
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first (cascades to their dependencies)
        ShareDistributionProtocol.registerFactory(manager);
        SecureFindMinProtocol.registerFactory(manager);
        SecureFindMaxProtocol.registerFactory(manager);
        SecureDotProductProtocol.registerFactory(manager);
        ReconstructSecretProtocol.registerFactory(manager);
        
        // Initiator only
        manager.registerProtocolFactory(PROTOCOL_TYPE, () -> new MPCArrayTestProtocol(), null);
    }
    
    private String protocolId;
    private int agentId;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    private long prime;
    private int threshold;
    private int s;
    private String rSecretId;
    private IMPCArrayTestListener listener;
    private int testNumber;
    
    // Test state
    private enum TestPhase {
        DISTRIBUTING,
        COMPUTING,           // FindMin, FindMax, DotProduct all in parallel
        RECONSTRUCTING,
        VERIFYING,
        COMPLETE
    }
    
    private TestPhase currentPhase;
    private boolean complete;
    private boolean successful;
    
    // Array data
    private int arraySize;
    private long[] arrayA;         // First array (for FindMin, FindMax, and DotProduct)
    private long[] arrayB;         // Second array (for DotProduct)
    private String baseArrayAId;
    private String baseArrayBId;
    private String minValueId;
    private String minIndexId;
    private String maxValueId;
    private String maxIndexId;
    private String dotProductId;
    
    // Tracking
    private Set<String> completedDistributions;
    private Set<String> completedProtocols;
    private Set<String> completedReconstructions;
    
    // Expected distribution count: arraySize for A + arraySize for B = 2 * arraySize
    private int expectedDistributions;
    
    // Reconstructed results
    private long reconstructedMinValue;
    private long reconstructedMinIndex;
    private long reconstructedMaxValue;
    private long reconstructedMaxIndex;
    private long reconstructedDotProduct;
    
    public MPCArrayTestProtocol() {
        this.completedDistributions = new HashSet<>();
        this.completedProtocols = new HashSet<>();
        this.completedReconstructions = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.shareStorage = (IShareStorage) params.get("shareStorage");
        this.participants = (List<Integer>) params.get("participants");
        this.prime = (Long) params.get("prime");
        this.threshold = (Integer) params.get("threshold");
        this.s = (Integer) params.get("s");
        this.rSecretId = (String) params.get("rSecretId");
        this.listener = (IMPCArrayTestListener) params.get("listener");
        this.testNumber = (Integer) params.get("testNumber");
        
        // Generate random array size between 2 and 10 (smaller for faster dot product)
        Random rng = new Random();
        this.arraySize = 2 + rng.nextInt(9);  // 2 to 10 inclusive
        this.arrayA = new long[arraySize];
        this.arrayB = new long[arraySize];
        
        // Generate random values (keep them smaller for dot product to avoid overflow)
        for (int i = 0; i < arraySize; i++) {
            arrayA[i] = Math.abs(rng.nextLong() % 1000);  // 0 to 999
            arrayB[i] = Math.abs(rng.nextLong() % 1000);  // 0 to 999
        }
        
        System.out.println("Array Test " + testNumber + ": Generated arrays of size " + arraySize);
        System.out.println("  Array A: " + Arrays.toString(arrayA));
        System.out.println("  Array B: " + Arrays.toString(arrayB));
        
        startDistributionPhase();
    }
    
    private void startDistributionPhase() {
        currentPhase = TestPhase.DISTRIBUTING;
        completedDistributions.clear();
        
        baseArrayAId = "array-test-" + testNumber + "-A";
        baseArrayBId = "array-test-" + testNumber + "-B";
        expectedDistributions = 2 * arraySize;  // A and B
        
        System.out.println("Array Test " + testNumber + ": Distributing " + expectedDistributions + 
                         " array elements (2 arrays)...");
        
        // Distribute array A
        for (int i = 0; i < arraySize; i++) {
            String elementId = baseArrayAId + "[" + i + "]";
            ShareDistributionProtocol.start(
                manager, arrayA[i], elementId, threshold, prime,
                participants, shareStorage, null, this  // null = sticky
            );
        }
        
        // Distribute array B
        for (int i = 0; i < arraySize; i++) {
            String elementId = baseArrayBId + "[" + i + "]";
            ShareDistributionProtocol.start(
                manager, arrayB[i], elementId, threshold, prime,
                participants, shareStorage, null, this  // null = sticky
            );
        }
    }
    
    @Override
    public void onShareDistributionComplete(String protocolId, String secretId) {
        if (currentPhase != TestPhase.DISTRIBUTING) return;
        
        completedDistributions.add(secretId);
        
        System.out.println("Array Test " + testNumber + ": Distributed " + 
                         completedDistributions.size() + "/" + expectedDistributions + " elements");
        
        if (completedDistributions.size() < expectedDistributions) {
            return;
        }
        
        startComputingPhase();
    }
    
    private void startComputingPhase() {
        currentPhase = TestPhase.COMPUTING;
        completedProtocols.clear();
        
        minValueId = "array-test-" + testNumber + "-min-val";
        minIndexId = "array-test-" + testNumber + "-min-idx";
        maxValueId = "array-test-" + testNumber + "-max-val";
        maxIndexId = "array-test-" + testNumber + "-max-idx";
        dotProductId = "array-test-" + testNumber + "-dotprod";
        
        System.out.println("Array Test " + testNumber + ": Starting FindMin, FindMax, and DotProduct in parallel...");
        
        // Start FindMin on array A
        SecureFindMinProtocol.start(
            manager, baseArrayAId, minValueId, minIndexId,
            0, arraySize - 1, rSecretId, prime, threshold,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start FindMax on array A
        SecureFindMaxProtocol.start(
            manager, baseArrayAId, maxValueId, maxIndexId,
            0, arraySize - 1, rSecretId, prime, threshold,
            participants, shareStorage, null, this  // null = sticky storage
        );
        
        // Start DotProduct: A · B
        SecureDotProductProtocol.start(
            manager, baseArrayAId, baseArrayBId, dotProductId,
            arraySize, rSecretId, prime, participants, shareStorage, null, this  // null = sticky storage
        );
    }
    
    @Override
    public void onSecureFindMinComplete(String protocolId, String valueId, String indexId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedProtocols.add("findmin");
        System.out.println("Array Test " + testNumber + ": FindMin complete (" + 
                         completedProtocols.size() + "/3)");
        
        checkComputingPhaseComplete();
    }
    
    @Override
    public void onSecureFindMaxComplete(String protocolId, String valueId, String indexId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedProtocols.add("findmax");
        System.out.println("Array Test " + testNumber + ": FindMax complete (" + 
                         completedProtocols.size() + "/3)");
        
        checkComputingPhaseComplete();
    }
    
    @Override
    public void onSecureDotProductComplete(String protocolId, String resultSecretId) {
        if (currentPhase != TestPhase.COMPUTING) return;
        
        completedProtocols.add("dotproduct");
        System.out.println("Array Test " + testNumber + ": DotProduct complete (" + 
                         completedProtocols.size() + "/3)");
        
        checkComputingPhaseComplete();
    }
    
    private void checkComputingPhaseComplete() {
        if (completedProtocols.size() < 3) {
            return;
        }
        startReconstructionPhase();
    }
    
    private void startReconstructionPhase() {
        currentPhase = TestPhase.RECONSTRUCTING;
        completedReconstructions.clear();
        
        System.out.println("Array Test " + testNumber + ": Reconstructing 5 results...");
        
        // Reconstruct all 5 results in parallel
        ReconstructSecretProtocol.start(
            manager, minValueId, prime, participants, shareStorage, this
        );
        ReconstructSecretProtocol.start(
            manager, minIndexId, prime, participants, shareStorage, this
        );
        ReconstructSecretProtocol.start(
            manager, maxValueId, prime, participants, shareStorage, this
        );
        ReconstructSecretProtocol.start(
            manager, maxIndexId, prime, participants, shareStorage, this
        );
        ReconstructSecretProtocol.start(
            manager, dotProductId, prime, participants, shareStorage, this
        );
    }
    
    @Override
    public void onReconstructComplete(String protocolId, String secretId, long value) {
        if (currentPhase != TestPhase.RECONSTRUCTING) return;
        
        if (secretId.equals(minValueId)) {
            reconstructedMinValue = value;
            completedReconstructions.add("min-value");
            System.out.println("Array Test " + testNumber + ": Min value reconstructed = " + value);
        } else if (secretId.equals(minIndexId)) {
            reconstructedMinIndex = value;
            completedReconstructions.add("min-index");
            System.out.println("Array Test " + testNumber + ": Min index reconstructed = " + value);
        } else if (secretId.equals(maxValueId)) {
            reconstructedMaxValue = value;
            completedReconstructions.add("max-value");
            System.out.println("Array Test " + testNumber + ": Max value reconstructed = " + value);
        } else if (secretId.equals(maxIndexId)) {
            reconstructedMaxIndex = value;
            completedReconstructions.add("max-index");
            System.out.println("Array Test " + testNumber + ": Max index reconstructed = " + value);
        } else if (secretId.equals(dotProductId)) {
            reconstructedDotProduct = value;
            completedReconstructions.add("dotproduct");
            System.out.println("Array Test " + testNumber + ": DotProduct reconstructed = " + value);
        }
        
        if (completedReconstructions.size() < 5) {
            return;
        }
        
        verifyAndComplete();
    }
    
    private void verifyAndComplete() {
        currentPhase = TestPhase.VERIFYING;
        
        // Find actual min and max in array A
        long expectedMinValue = arrayA[0];
        int expectedMinIndex = 0;
        long expectedMaxValue = arrayA[0];
        int expectedMaxIndex = 0;
        
        for (int i = 1; i < arraySize; i++) {
            if (arrayA[i] < expectedMinValue) {
                expectedMinValue = arrayA[i];
                expectedMinIndex = i;
            }
            if (arrayA[i] > expectedMaxValue) {
                expectedMaxValue = arrayA[i];
                expectedMaxIndex = i;
            }
        }
        
        // Compute expected dot product: sum of A[i] * B[i]
        long expectedDotProduct = 0;
        for (int i = 0; i < arraySize; i++) {
            expectedDotProduct += arrayA[i] * arrayB[i];
        }
        expectedDotProduct = expectedDotProduct % prime;  // Modular arithmetic
        
        boolean minValueCorrect = (reconstructedMinValue == expectedMinValue);
        boolean minIndexCorrect = (reconstructedMinIndex == expectedMinIndex);
        boolean maxValueCorrect = (reconstructedMaxValue == expectedMaxValue);
        boolean maxIndexCorrect = (reconstructedMaxIndex == expectedMaxIndex);
        boolean dotProductCorrect = (reconstructedDotProduct == expectedDotProduct);
        boolean passed = minValueCorrect && minIndexCorrect && maxValueCorrect && 
                        maxIndexCorrect && dotProductCorrect;
        
        System.out.println("Array Test " + testNumber + ": Verification phase");
        System.out.println("  Array size: " + arraySize);
        System.out.println("  Array A: " + Arrays.toString(arrayA));
        System.out.println("  Array B: " + Arrays.toString(arrayB));
        System.out.println("  Expected min value: " + expectedMinValue + ", got: " + reconstructedMinValue + 
                         " [" + (minValueCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected min index: " + expectedMinIndex + ", got: " + (int)reconstructedMinIndex + 
                         " [" + (minIndexCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected max value: " + expectedMaxValue + ", got: " + reconstructedMaxValue + 
                         " [" + (maxValueCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected max index: " + expectedMaxIndex + ", got: " + (int)reconstructedMaxIndex + 
                         " [" + (maxIndexCorrect ? "✓" : "✗") + "]");
        System.out.println("  Expected dot product: " + expectedDotProduct + ", got: " + reconstructedDotProduct + 
                         " [" + (dotProductCorrect ? "✓" : "✗") + "]");
        System.out.println("  Overall: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        
        complete = true;
        successful = passed;
        
        if (listener != null) {
            listener.onArrayTestComplete(this.protocolId, testNumber, passed);
        }
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // This protocol doesn't handle messages directly
        // All messages are handled by sub-protocols
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
        return TestingConstants.MPC_ARRAY_TEST;
    }
    
    @Override
    public Object getResult() {
        return successful ? "PASSED" : "FAILED";
    }
}
