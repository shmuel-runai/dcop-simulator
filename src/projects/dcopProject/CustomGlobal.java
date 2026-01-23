package projects.dcopProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JOptionPane;

import dcop.common.DCOPProblem;
import dcop.common.configuration.RandomNetworkConfiguration;
import dcop.common.TestResults;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;
import dcop.algorithms.AlgorithmType;
import dcop.algorithms.basic.BasicNetworkBuilder;
import dcop.algorithms.dsa.DSANetworkBuilder;
import dcop.algorithms.pdsa.PDSANetworkBuilder;
import dcop.algorithms.pmgm.PMGMNetworkBuilder;
import sinalgo.configuration.Configuration;
import sinalgo.nodes.Node;
import sinalgo.nodes.Position;
import sinalgo.runtime.AbstractCustomGlobal;
import sinalgo.runtime.Global;

import sinalgo.tools.Tools;

/**
 * Custom global state and methods for the DCOP testing framework.
 * 
 * Provides GUI button and methods to run DCOP algorithm tests.
 */
public class CustomGlobal extends AbstractCustomGlobal {
    
    // Simulation state
    private RandomNetworkConfiguration config;
    private TestResults results;
    private DCOPProblem currentProblem;
    
    // Algorithm selection
    private AlgorithmType selectedAlgorithm;
    private double dsaStochastic = 0.8;  // DSA stochastic parameter
    private boolean testRunning;
    private int currentIteration;
    private long iterationStartTime;
    private long iterationTimeoutMs;
    private boolean iterationInProgress;
    private boolean iterationTimerStarted;  // Flag to track if timer has started for current iteration
    private int totalIterations;
    private long[] iterationActualRuntimes;  // Track actual wall-clock time per iteration
    
    /**
     * Constructor - initialize configuration from Config.xml.
     */
    public CustomGlobal() {
        // Read all parameters from config with defaults
        int numIterations = 10;
        long timeoutMs = 60000;
        long problemSeed = 1000;
        long algorithmSeed = 2000;
        int numAgents = 10;
        int domainSize = 5;
        int minCost = 0;
        int maxCost = 100;
        double networkDensity = 0.3;
        
        try {
            numIterations = Configuration.getIntegerParameter("DCOPTest/numIterations");
        } catch (Exception e) {
            System.out.println("Could not read numIterations from config, using default: " + numIterations);
        }
        
        try {
            int timeoutSeconds = Configuration.getIntegerParameter("DCOPTest/timeoutSeconds");
            timeoutMs = timeoutSeconds * 1000L;
        } catch (Exception e) {
            System.out.println("Could not read timeoutSeconds from config, using default: " + (timeoutMs/1000) + " seconds");
        }
        
        try {
            problemSeed = Configuration.getIntegerParameter("DCOPTest/problemSeed");
        } catch (Exception e) {
            System.out.println("Could not read problemSeed from config, using default: " + problemSeed);
        }
        
        try {
            algorithmSeed = Configuration.getIntegerParameter("DCOPTest/algorithmSeed");
        } catch (Exception e) {
            System.out.println("Could not read algorithmSeed from config, using default: " + algorithmSeed);
        }
        
        try {
            numAgents = Configuration.getIntegerParameter("DCOPTest/numAgents");
        } catch (Exception e) {
            System.out.println("Could not read numAgents from config, using default: " + numAgents);
        }
        
        try {
            domainSize = Configuration.getIntegerParameter("DCOPTest/domainSize");
        } catch (Exception e) {
            System.out.println("Could not read domainSize from config, using default: " + domainSize);
        }
        
        try {
            minCost = Configuration.getIntegerParameter("DCOPTest/minCost");
        } catch (Exception e) {
            System.out.println("Could not read minCost from config, using default: " + minCost);
        }
        
        try {
            maxCost = Configuration.getIntegerParameter("DCOPTest/maxCost");
        } catch (Exception e) {
            System.out.println("Could not read maxCost from config, using default: " + maxCost);
        }
        
        try {
            networkDensity = Configuration.getDoubleParameter("DCOPTest/networkDensity");
        } catch (Exception e) {
            System.out.println("Could not read networkDensity from config, using default: " + networkDensity);
        }
        
        // Create configuration from parsed values
        this.config = new RandomNetworkConfiguration(
            numIterations,
            timeoutMs,
            problemSeed,
            algorithmSeed,
            numAgents,
            domainSize,
            minCost,
            maxCost,
            networkDensity
        );
        this.testRunning = false;
        this.currentIteration = 0;
        this.iterationInProgress = false;
        
        // Read algorithm selection from config
        try {
            String algoName = Configuration.getStringParameter("DCOPTest/algorithm");
            this.selectedAlgorithm = AlgorithmType.fromString(algoName);
            System.out.println("Configured algorithm: " + selectedAlgorithm);
            
            if (selectedAlgorithm == AlgorithmType.DSA || selectedAlgorithm == AlgorithmType.PDSA) {
                this.dsaStochastic = Configuration.getDoubleParameter("DCOPTest/dsaStochastic");
                System.out.println("Stochastic parameter: " + dsaStochastic);
            }
        } catch (Exception e) {
            // Default to BASIC if not specified or error
            System.out.println("Could not read algorithm config, defaulting to BASIC. Error: " + e.getMessage());
            this.selectedAlgorithm = AlgorithmType.BASIC;
        }
    }
    
    /**
     * Determines if the simulation has terminated.
     * Checks if current iteration has reached its timeout.
     */
    @Override
    public boolean hasTerminated() {
        if (!iterationInProgress) {
            return false; // Not running an iteration
        }
        
        // Check if iteration timeout has been reached
        long elapsed = System.currentTimeMillis() - iterationStartTime;
        if (elapsed >= iterationTimeoutMs) {
            // Timeout reached - finish this iteration and move to next
            finishCurrentIteration();
            return false; // Continue simulation for next iteration
        }
        
        return false;
    }
    
    /**
     * Called before simulation starts.
     */
    @Override
    public void preRun() {
        // Configuration is hardcoded in the constructor
        System.out.println("DCOP Test Framework initialized.");
        System.out.println("Configuration: " + config);
        
        // In batch mode, automatically start the test
        if (!Global.isGuiMode) {
            System.out.println("Batch mode detected - starting test automatically...");
            runDCOPTest();
        }
    }
    
    /**
     * Creates a network builder for the selected algorithm.
     * 
     * @param algorithm The algorithm type
     * @param algorithmSeed The seed for algorithm randomization
     * @return Network builder instance
     */
    private IDCOPNetworkBuilder createNetworkBuilder(AlgorithmType algorithm, long algorithmSeed) {
        switch (algorithm) {
            case BASIC:
                return new BasicNetworkBuilder(algorithmSeed);
            case DSA:
                return new DSANetworkBuilder(algorithmSeed, dsaStochastic);
            case PDSA:
                return new PDSANetworkBuilder(algorithmSeed, dsaStochastic);
            case PMGM:
                return new PMGMNetworkBuilder(algorithmSeed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
    
    /**
     * Custom button to start DCOP testing with Basic algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (Basic)", toolTipText="Start DCOP test with Basic random algorithm")
    public void runBasicTest() {
        selectedAlgorithm = AlgorithmType.BASIC;
        runDCOPTest();
    }
    
    /**
     * Custom button to start DCOP testing with DSA algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (DSA)", toolTipText="Start DCOP test with DSA algorithm")
    public void runDSATest() {
        selectedAlgorithm = AlgorithmType.DSA;
        runDCOPTest();
    }
    
    /**
     * Custom button to start DCOP testing with P-DSA algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (P-DSA)", toolTipText="Start DCOP test with privacy-preserving P-DSA algorithm")
    public void runPDSATest() {
        selectedAlgorithm = AlgorithmType.PDSA;
        runDCOPTest();
    }
    
    /**
     * Custom button to start DCOP testing with P-MGM algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (P-MGM)", toolTipText="Start DCOP test with privacy-preserving P-MGM algorithm")
    public void runPMGMTest() {
        selectedAlgorithm = AlgorithmType.PMGM;
        runDCOPTest();
    }
    
    /**
     * Runs DCOP testing with the selected algorithm.
     * Initializes the test suite and starts the first iteration.
     */
    private void runDCOPTest() {
        if (testRunning) {
            if (Global.isGuiMode) {
                JOptionPane.showMessageDialog(null, 
                    "Test is already running!", 
                    "DCOP Test", 
                    JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        
        System.out.println("\n========================================");
        System.out.println("Starting DCOP Test Suite - Algorithm: " + selectedAlgorithm);
        System.out.println("========================================");
        
        // Initialize test state
        testRunning = true;
        currentIteration = 0;
        totalIterations = config.numIterations;
        results = new TestResults(config);
        iterationActualRuntimes = new long[totalIterations];
        
        // Start first iteration
        startIteration();
    }
    
    /**
     * Starts the current iteration in the test suite.
     * Called initially and after previous iteration completes.
     */
    private void startIteration() {
        if (currentIteration >= totalIterations) {
            // All iterations complete
            finishTestSuite();
            return;
        }
        
        System.out.println("\n--- Iteration " + (currentIteration + 1) + " ---");
        
        // Calculate seeds for this iteration (0-based currentIteration)
        long problemSeed = config.problemSeed + currentIteration;
        long algorithmSeed = config.algorithmSeed + currentIteration;
        
        // Generate new DCOP problem using configuration
        currentProblem = config.generateProblem(problemSeed);
        System.out.println("Generated problem: " + currentProblem);
        
        // Build network from problem using selected algorithm
        IDCOPNetworkBuilder networkBuilder = createNetworkBuilder(selectedAlgorithm, algorithmSeed);
        DCOPNetwork network = networkBuilder.buildNetwork(currentProblem);
        System.out.println("Built network: " + network + " with " + selectedAlgorithm + " algorithm");
        
        // Deploy network to Sinalgo runtime
        deployNetwork(network);
        
        // Start all agents
        Enumeration<?> nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            agent.startAlgorithm();
        }
        
        // Set up iteration state - start timer immediately
        iterationTimeoutMs = config.timeoutMs;
        iterationInProgress = true;
        iterationStartTime = System.currentTimeMillis();  // Start timer NOW

        
        System.out.println("Running iteration for up to " + (iterationTimeoutMs / 1000) + " seconds...");
        
        // The simulation will now run rounds until hasTerminated() returns true
        // For batch mode, the runtime is already running
        // For GUI mode, user needs to click "Run" button
    }
    
    /**
     * Finishes the current iteration and moves to the next one.
     * Called when hasTerminated() triggers.
     */
    private void finishCurrentIteration() {
        if (!iterationInProgress) {
            return;
        }
        
        iterationInProgress = false;
        
        // Calculate actual wall-clock runtime
        long actualRuntime = System.currentTimeMillis() - iterationStartTime;
        iterationActualRuntimes[currentIteration] = actualRuntime;
        
        // Stop all agents
        Enumeration<?> nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            agent.stopAlgorithm();
        }
        
        // Collect agent values
        int[] agentValues = new int[config.numAgents + 1]; // Size N+1, index 0 unused
        nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            agentValues[agent.getID()] = agent.getSelectedValue();
        }
        
        // Calculate total cost
        int totalCost = currentProblem.getTotalCost(agentValues);
        
        // Collect round statistics
        int minRounds = Integer.MAX_VALUE;
        int maxRoundsActual = 0;
        int totalRounds = 0;
        int agentsWithRounds = 0;
        nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            int rounds = agent.getProperty("rounds");
            if (rounds >= 0) {  // Only count if supported
                totalRounds += rounds;
                minRounds = Math.min(minRounds, rounds);
                maxRoundsActual = Math.max(maxRoundsActual, rounds);
                agentsWithRounds++;
            }
        }
        
        // Store result (use 1-based iteration number for display)
        // If no agents reported rounds, use -1 to indicate algorithm doesn't use rounds
        int maxRoundsForResult = (agentsWithRounds > 0) ? maxRoundsActual : -1;
        results.addResult(currentIteration + 1, actualRuntime, totalCost, maxRoundsForResult, agentValues);
        
        // Log iteration result (no dialog - just console output)
        System.out.println("Iteration " + (currentIteration + 1) + "/" + totalIterations + 
                          " complete: Cost=" + totalCost + ", Runtime=" + actualRuntime + "ms");
        
        // Log round statistics if available
        if (agentsWithRounds > 0) {
            double avgRounds = (double) totalRounds / agentsWithRounds;
            System.out.println("  Rounds: min=" + minRounds + ", max=" + maxRoundsActual + 
                              ", avg=" + String.format("%.1f", avgRounds));
        }
        
        // CLEANUP: Free memory before next iteration
        cleanupIteration();
        
        // Move to next iteration
        currentIteration++;
        startIteration();
    }
    
    /**
     * Cleans up resources from the current iteration to free memory.
     * Called between iterations to prevent OutOfMemoryError.
     */
    private void cleanupIteration() {
        // Remove all nodes (agents) from the runtime
        // This triggers edge removal and clears node references
        Tools.removeAllNodes();
        
        // Clear Sinalgo's recycled objects (EdgePool, Packets, Events)
        // This prevents memory accumulation from object pooling
        Tools.disposeRecycledObjects(null);
        
        // Clear reference to old problem
        currentProblem = null;
        
        // Aggressive garbage collection: call multiple times with brief pauses
        // Java's System.gc() is just a hint, so we try multiple times
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(100); // Give GC time to run
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        // Log memory status
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        System.out.println("Memory after cleanup: " + usedMemory + "MB / " + maxMemory + "MB");
    }
    
    /**
     * Finishes the entire test suite and saves results.
     */
    private void finishTestSuite() {
        testRunning = false;
        
        System.out.println("\n========================================");
        System.out.println("Test Suite Completed");
        System.out.println("========================================");
        
        // Save and display results
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            String filename = TestResults.generateTimestampedFilename(selectedAlgorithm.name());
            results.printSummary();
            results.writeToCSV(filename);
            
            if (Global.isGuiMode) {
                JOptionPane.showMessageDialog(null,
                    String.format("Test completed!\n\n" +
                                  "Iterations: %d\n" +
                                  "Average Cost: %.1f\n" +
                                  "Average Runtime: %.1f ms\n\n" +
                                  "Results saved to:\n%s",
                                  totalIterations,
                                  results.getAverageCost(),
                                  results.getAverageRuntime(),
                                  filename),
                    "DCOP Test Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                // Batch mode - exit after tests complete
                System.out.println("\nBatch test completed. Exiting.");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("Error saving results: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    /**
     * Deploys a DCOP network to the Sinalgo runtime.
     * Handles all Sinalgo-specific setup: clearing old state, positioning, 
     * adding to runtime, and building edges.
     * 
     * @param network The network to deploy
     */
    private void deployNetwork(DCOPNetwork network) {
        // Reset node ID counter to ensure IDs 1 to N
        Node.resetIDCounter();
        
        // Clear all existing nodes
        Tools.removeAllNodes();
        
        // Add agents to runtime with random positions using for-each
        for (IDCOPAgent agent : network) {
            try {
                // Cast to Node for Sinalgo operations
                // This is safe because all IDCOPAgent implementations extend Node
                Node node = (Node) agent;
                
                // Set random position (2D)
                Position pos = new Position(
                    Tools.getRandomNumberGenerator().nextDouble() * Configuration.dimX,
                    Tools.getRandomNumberGenerator().nextDouble() * Configuration.dimY,
                    0  // Z = 0 for 2D
                );
                node.setPosition(pos);
                
                // Add to runtime (this calls init() and Runtime.addNode())
                // After this call, node.ID will be assigned (1 to N in order)
                node.finishInitializationWithDefaultModels(true);
                
            } catch (Exception e) {
                System.err.println("Error deploying agent: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Deployed " + Tools.getNodeList().size() + " agents");
        
        // Build FULL MESH connections (required for MPC protocols)
        // MPC protocols need every agent to communicate with every other agent
        int edgesCreated = 0;
        List<Node> allNodes = new ArrayList<>();
        for (IDCOPAgent agent : network) {
            allNodes.add((Node) agent);
        }
        
        for (int i = 0; i < allNodes.size(); i++) {
            Node nodeI = allNodes.get(i);
            for (int j = 0; j < allNodes.size(); j++) {
                if (i != j) {
                    Node nodeJ = allNodes.get(j);
                    // Create explicit directed edge from nodeI to nodeJ
                    nodeI.outgoingConnections.add(nodeI, nodeJ, false);
                    edgesCreated++;
                }
            }
        }
        
        System.out.println("Created " + edgesCreated + " directed edges (full mesh)");
    }
    
    /**
     * Called at the beginning of each round.
     */
    @Override
    public void preRound() {
        // Timer is now started in startNextIteration(), nothing needed here
    }
    
    /**
     * Called at the end of each round.
     */
    @Override
    public void postRound() {
        // Could check for iteration timeout here if needed
    }
    
    /**
     * Called when the application exits.
     */
    @Override
    public void onExit() {
        // Cleanup if needed
    }
    
    /**
     * Checks project requirements.
     */
    @Override
    public void checkProjectRequirements() {
        // Could verify configuration here
    }
}
