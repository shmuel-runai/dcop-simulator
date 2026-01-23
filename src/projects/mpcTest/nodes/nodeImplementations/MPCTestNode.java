package projects.mpcTest.nodes.nodeImplementations;

import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import utils.protocols.adapters.sinalgo.SinalgoMessageTransport;
import utils.protocols.adapters.sinalgo.SinalgoProtocolMessageWrapper;
import utils.protocols.core.*;
import utils.protocols.testing.IMPCSingleTestListener;
import utils.protocols.testing.IMPCArrayTestListener;
import utils.protocols.testing.TestingProtocolHelper;
import utils.crypto.secretsharing.ShareStorageManager;

import java.awt.Color;
import java.awt.Graphics;
import java.util.*;

/**
 * Simplified node implementation for MPC testing.
 * 
 * This node demonstrates protocol composition by using MPCSingleTestProtocol
 * to orchestrate all test operations. The node simply:
 * 1. Kicks off test protocols
 * 2. Tracks pass/fail statistics
 * 3. Reports final results
 * 
 * All the complex orchestration logic has been moved into MPCSingleTestProtocol.
 */
public class MPCTestNode extends Node implements IMPCSingleTestListener, IMPCArrayTestListener {
    
    // Protocol framework components
    private DistributedProtocolManager protocolManager;
    private ShareStorageManager shareStorage;
    private SinalgoMessageTransport transport;
    
    // Test orchestration (only for node ID 1)
    private boolean isTestCoordinator = false;
    private static final int SINGLE_TEST_ITERATIONS = 100;
    private static final int ARRAY_TEST_ITERATIONS = 100;
    private static final long PRIME = 2147483647L; // 2^31 - 1 (Mersenne prime for IsZero protocol)
    private static final int S = 31; // Exponent for prime = 2^s - 1
    private static final int THRESHOLD = 3;
    
    // Test phase tracking
    private enum TestPhase {
        SINGLE_TESTS,
        ARRAY_TESTS,
        COMPLETE
    }
    
    private TestPhase currentTestPhase = TestPhase.SINGLE_TESTS;
    private int singleTestIteration = 0;
    private int arrayTestIteration = 0;
    
    // Test statistics
    private int singleTestsPassed = 0;
    private int singleTestsFailed = 0;
    private int arrayTestsPassed = 0;
    private int arrayTestsFailed = 0;
    private long totalTestTime = 0;
    private long testStartTime = 0;
    
    @Override
    public void init() {
        // Initialize share storage
        this.shareStorage = new ShareStorageManager();
        
        // Initialize message transport
        this.transport = new SinalgoMessageTransport(this);
        
        // Initialize protocol manager
        this.protocolManager = new DistributedProtocolManager(transport, this.ID);
        
        // Register Testing protocol factories (cascades to all MPC dependencies)
        TestingProtocolHelper.registerTestingProtocols(this.protocolManager);
        
        // Set up local message callback for self-messages (same as PDSA/PMGM)
        transport.setLocalMessageCallback((msg, senderId) -> {
            protocolManager.handleIncomingMessage(msg, senderId, buildResources());
        });
        
        // Node 1 is the test coordinator
        if (this.ID == 1) {
            this.isTestCoordinator = true;
            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║     MPC Test Suite Starting            ║");
            System.out.println("║     (Using Protocol Composition!)      ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  Coordinator: Node " + this.ID + "                   ║");
            System.out.println("║  Single tests: " + SINGLE_TEST_ITERATIONS + "                    ║");
            System.out.println("║  Array tests:  " + ARRAY_TEST_ITERATIONS + "                    ║");
            System.out.println("║  Network size: 10 nodes                ║");
            System.out.println("║  Threshold: " + THRESHOLD + "                         ║");
            System.out.println("║  Prime: " + String.format("%,d", PRIME) + "                  ║");
            System.out.println("╚════════════════════════════════════════╝\n");
        }
    }
    
    /**
     * Builds the list of all participants (all nodes in the MPC protocol).
     * Uses neighborsId() which returns all nodes including self.
     * 
     * @return Sorted list of all participant node IDs
     */
    private List<Integer> buildParticipants() {
        // getParticipants() returns all neighbors + self
        return transport.getParticipants();
    }
    
    @Override
    public void preStep() {
        // Test coordinator starts tests based on phase
        if (!isTestCoordinator) return;
        
        if (currentTestPhase == TestPhase.SINGLE_TESTS && singleTestIteration == 0) {
            startNextSingleTest();
        } else if (currentTestPhase == TestPhase.ARRAY_TESTS && arrayTestIteration == 0) {
            startNextArrayTest();
        }
    }
    
    @Override
    public void neighborhoodChange() {
        // Not used
    }
    
    @Override
    public void postStep() {
        // Not used
    }
    
    /**
     * Builds the resources map needed for protocol message handling.
     * 
     * @return Map of resources for protocol handling
     */
    private Map<String, Object> buildResources() {
        Map<String, Object> resources = new HashMap<>();
        resources.put("shareStorage", shareStorage);
        // NOTE: "participants" removed - manager derives from transport.neighborsId()
        return resources;
    }
    
    @Override
    public void handleMessages(Inbox inbox) {
        // Route protocol messages to the protocol manager
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            
            if (msg instanceof SinalgoProtocolMessageWrapper) {
                SinalgoProtocolMessageWrapper wrapper = (SinalgoProtocolMessageWrapper) msg;
                IProtocolMessage protocolMsg = wrapper.unwrap();
                int senderId = inbox.getSender().ID;
                
                // Route to protocol manager with all required resources
                protocolManager.handleIncomingMessage(protocolMsg, senderId, buildResources());
            }
        }
    }
    
    /**
     * Starts the next single MPC test iteration.
     */
    private void startNextSingleTest() {
        if (singleTestIteration >= SINGLE_TEST_ITERATIONS) {
            transitionToArrayTests();
            return;
        }
        
        singleTestIteration++;
        testStartTime = System.currentTimeMillis();
        
        // Simply start the test protocol - it handles all the orchestration!
        TestingProtocolHelper.startMPCSingleTest(
            protocolManager,
            singleTestIteration,
            PRIME,
            S,
            THRESHOLD,
            buildParticipants(),
            shareStorage,
            this
        );
    }
    
    /**
     * Called when a single MPC test completes.
     */
    @Override
    public void onTestComplete(String protocolId, int testNumber, boolean passed) {
        if (!isTestCoordinator) return;
        
        long testTime = System.currentTimeMillis() - testStartTime;
        totalTestTime += testTime;
        
        if (passed) {
            singleTestsPassed++;
        } else {
            singleTestsFailed++;
        }
        
        System.out.println("\n>>> Single Test " + testNumber + ": " + 
                         (passed ? "✓ PASSED" : "✗ FAILED") + 
                         " (completed in " + testTime + " ms) <<<\n");
        
        protocolManager.removeProtocol(protocolId);
        startNextSingleTest();
    }
    
    /**
     * Transitions from single tests to array tests.
     */
    private void transitionToArrayTests() {
        currentTestPhase = TestPhase.ARRAY_TESTS;
        
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║  Single Tests Complete                 ║");
        System.out.println("║  Passed: " + String.format("%3d", singleTestsPassed) + "/" + SINGLE_TEST_ITERATIONS + "                        ║");
        System.out.println("║  Starting Array Tests (FindMin)...    ║");
        System.out.println("╚════════════════════════════════════════╝\n");
        
        startNextArrayTest();
    }
    
    /**
     * Starts the next array test iteration.
     */
    private void startNextArrayTest() {
        if (arrayTestIteration >= ARRAY_TEST_ITERATIONS) {
            printFinalResults();
            currentTestPhase = TestPhase.COMPLETE;
            return;
        }
        
        arrayTestIteration++;
        testStartTime = System.currentTimeMillis();
        
        // Start the array test protocol
        TestingProtocolHelper.startMPCArrayTest(
            protocolManager,
            arrayTestIteration,
            PRIME,
            S,
            THRESHOLD,
            "r-secret",  // Re-use the r-secret from single tests
            buildParticipants(),
            shareStorage,
            this
        );
    }
    
    /**
     * Called when an array test completes.
     */
    @Override
    public void onArrayTestComplete(String protocolId, int testNumber, boolean passed) {
        if (!isTestCoordinator) return;
        
        long testTime = System.currentTimeMillis() - testStartTime;
        totalTestTime += testTime;
        
        if (passed) {
            arrayTestsPassed++;
        } else {
            arrayTestsFailed++;
        }
        
        System.out.println("\n>>> Array Test " + testNumber + ": " + 
                         (passed ? "✓ PASSED" : "✗ FAILED") + 
                         " (completed in " + testTime + " ms) <<<\n");
        
        protocolManager.removeProtocol(protocolId);
        startNextArrayTest();
    }
    
    /**
     * Prints final test results.
     */
    private void printFinalResults() {
        int totalTests = SINGLE_TEST_ITERATIONS + ARRAY_TEST_ITERATIONS;
        int totalPassed = singleTestsPassed + arrayTestsPassed;
        int totalFailed = singleTestsFailed + arrayTestsFailed;
        double singleSuccessRate = (100.0 * singleTestsPassed / SINGLE_TEST_ITERATIONS);
        double arraySuccessRate = (ARRAY_TEST_ITERATIONS > 0) ? (100.0 * arrayTestsPassed / ARRAY_TEST_ITERATIONS) : 0;
        double overallSuccessRate = (100.0 * totalPassed / totalTests);
        long avgTime = (totalTests > 0) ? (totalTestTime / totalTests) : 0;
        
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║        MPC TEST SUITE COMPLETE                     ║");
        System.out.println("║     (Powered by Protocol Composition!)             ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  SINGLE TESTS (MPC Operations):                    ║");
        System.out.println("║    Total:             " + String.format("%-28d", SINGLE_TEST_ITERATIONS) + "║");
        System.out.println("║    Passed:            " + String.format("%-28d", singleTestsPassed) + "║");
        System.out.println("║    Failed:            " + String.format("%-28d", singleTestsFailed) + "║");
        System.out.println("║    Success rate:      " + String.format("%-25.2f", singleSuccessRate) + " % ║");
        System.out.println("║                                                    ║");
        System.out.println("║  ARRAY TESTS (FindMin Protocol):                  ║");
        System.out.println("║    Total:             " + String.format("%-28d", ARRAY_TEST_ITERATIONS) + "║");
        System.out.println("║    Passed:            " + String.format("%-28d", arrayTestsPassed) + "║");
        System.out.println("║    Failed:            " + String.format("%-28d", arrayTestsFailed) + "║");
        System.out.println("║    Success rate:      " + String.format("%-25.2f", arraySuccessRate) + " % ║");
        System.out.println("║                                                    ║");
        System.out.println("║  OVERALL:                                          ║");
        System.out.println("║    Total tests:       " + String.format("%-28d", totalTests) + "║");
        System.out.println("║    Tests passed:      " + String.format("%-28d", totalPassed) + "║");
        System.out.println("║    Tests failed:      " + String.format("%-28d", totalFailed) + "║");
        System.out.println("║    Success rate:      " + String.format("%-25.2f", overallSuccessRate) + " % ║");
        System.out.println("║    Average test time: " + String.format("%-25d", avgTime) + " ms ║");
        System.out.println("║    Total time:        " + String.format("%-25d", totalTestTime) + " ms ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");
        
        if (totalFailed > 0) {
            System.err.println("⚠️  WARNING: " + totalFailed + " test(s) failed!");
        } else {
            System.out.println("✓ All tests passed successfully!");
        }
        
        // Stop the simulation
        sinalgo.runtime.Global.isRunning = false;
    }
    
    @Override
    public void draw(Graphics g, sinalgo.gui.transformation.PositionTransformation pt, boolean highlight) {
        // Draw the node as a circle
        String text = String.valueOf(this.ID);
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 16, Color.YELLOW);
        
        // Test coordinator is blue
        if (isTestCoordinator) {
            super.drawNodeAsDiskWithText(g, pt, highlight, text, 16, Color.BLUE);
        }
    }
    
    @Override
    public void checkRequirements() {
        // No special requirements
    }
    
    @Override
    public String toString() {
        return "MPCTestNode " + this.ID;
    }
    
    /**
     * Returns the share storage for this node.
     * Used by CustomGlobal to pre-distribute r-secret shares.
     */
    public ShareStorageManager getShareStorage() {
        return shareStorage;
    }
}
