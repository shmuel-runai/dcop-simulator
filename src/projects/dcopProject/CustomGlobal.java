package projects.dcopProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JOptionPane;

import dcop.common.DCOPProblem;
import dcop.common.configuration.ISimulationConfiguration;
import dcop.common.configuration.NetworkType;
import dcop.common.configuration.RandomNetworkConfiguration;
import dcop.common.configuration.ScaleFreeConfiguration;
import dcop.common.TestResults;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;
import dcop.algorithms.AlgorithmType;
import dcop.algorithms.basic.BasicNetworkBuilder;
import dcop.algorithms.dsa.DSANetworkBuilder;
import dcop.algorithms.pdsa.PDSANetworkBuilder;
import dcop.algorithms.pmgm.PMGMNetworkBuilder;
import dcop.algorithms.pmaxsum.PMaxSumNetworkBuilder;
import dcop.algorithms.maxsum.MaxSumNetworkBuilder;
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
    private ISimulationConfiguration config;
    private int numAgents;  // Cached for node filtering
    private int numIterations;  // Cached for iteration control
    private long timeoutMs;  // Cached for timeout
    private long problemSeed;  // Base seed for problem generation
    private long algorithmSeed;  // Base seed for algorithm randomness
    private TestResults results;
    private DCOPProblem currentProblem;
    
    // Algorithm selection
    private AlgorithmType selectedAlgorithm;
    private double dsaStochastic = 0.8;  // DSA stochastic parameter
    private int lastRound = -1;          // -1 = timeout-based halting, >0 = round-based halting
    private IDCOPNetworkBuilder currentNetworkBuilder;  // For post-deploy wiring (MAXSUM/P-MAXSUM)
    private boolean testRunning;
    private int currentIteration;
    private long iterationStartTime;
    private long iterationTimeoutMs;
    private boolean iterationInProgress;
    private boolean iterationTimerStarted;  // Flag to track if timer has started for current iteration
    private int totalIterations;
    private long[] iterationActualRuntimes;  // Track actual wall-clock time per iteration
    
    // Output configuration
    private String outputPrefix = "";     // Prefix for output files
    private boolean exportProblems = false;  // Whether to export problem cost matrices
    private long currentProblemSeed;      // Seed used for current problem
    
    // Cached config values for TestConfig export
    private int domainSizeConfig;
    private int minCostConfig;
    private int maxCostConfig;
    private double networkDensityConfig;
    private int initCliqueConfig;
    private int additionConfig;
    private NetworkType networkTypeConfig;
    
    /**
     * Constructor - initialize configuration from Config.xml.
     */
    public CustomGlobal() {
        // Read all parameters from config with defaults
        int numIterations = 10;
        long timeoutMs = 60000;
        long problemSeed = 1000;
        long algorithmSeed = 2000;
        int numAgentsConfig = 10;
        int domainSize = 5;
        int minCost = 0;
        int maxCost = 10;
        double networkDensity = 0.3;
        
        // Scale-free network parameters
        NetworkType networkType = NetworkType.RANDOM;
        int initClique = 4;
        int addition = 2;
        
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
            numAgentsConfig = Configuration.getIntegerParameter("DCOPTest/numAgents");
        } catch (Exception e) {
            System.out.println("Could not read numAgents from config, using default: " + numAgentsConfig);
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
        
        try {
            this.lastRound = Configuration.getIntegerParameter("DCOPTest/lastRound");
        } catch (Exception e) {
            System.out.println("Could not read lastRound from config, using default: " + lastRound);
        }
        
        // Read network topology type
        try {
            String networkTypeStr = Configuration.getStringParameter("DCOPTest/networkType");
            networkType = NetworkType.fromString(networkTypeStr);
            System.out.println("Network type: " + networkType);
        } catch (Exception e) {
            System.out.println("Could not read networkType from config, using default: " + networkType);
        }
        
        // Read scale-free parameters (only used if networkType is SCALE_FREE)
        try {
            initClique = Configuration.getIntegerParameter("DCOPTest/initClique");
        } catch (Exception e) {
            System.out.println("Could not read initClique from config, using default: " + initClique);
        }
        
        try {
            addition = Configuration.getIntegerParameter("DCOPTest/addition");
        } catch (Exception e) {
            System.out.println("Could not read addition from config, using default: " + addition);
        }
        
        // Store simulation parameters for use in test loop
        this.numAgents = numAgentsConfig;
        this.numIterations = numIterations;
        this.timeoutMs = timeoutMs;
        this.problemSeed = problemSeed;
        this.algorithmSeed = algorithmSeed;
        
        // Store config values for TestConfig export
        this.domainSizeConfig = domainSize;
        this.minCostConfig = minCost;
        this.maxCostConfig = maxCost;
        this.networkDensityConfig = networkDensity;
        this.initCliqueConfig = initClique;
        this.additionConfig = addition;
        this.networkTypeConfig = networkType;
        
        // Read output configuration
        try {
            this.outputPrefix = Configuration.getStringParameter("DCOPTest/outputPrefix");
            System.out.println("Output prefix: " + outputPrefix);
        } catch (Exception e) {
            // No prefix specified, use default
        }
        
        try {
            String exportStr = Configuration.getStringParameter("DCOPTest/exportProblems");
            this.exportProblems = "true".equalsIgnoreCase(exportStr) || "1".equals(exportStr);
            if (exportProblems) {
                System.out.println("Problem export: enabled");
            }
        } catch (Exception e) {
            // Export disabled by default
        }
        
        // Create configuration based on network type
        if (networkType == NetworkType.SCALE_FREE) {
            this.config = new ScaleFreeConfiguration(
                numAgentsConfig,
                domainSize,
                minCost,
                maxCost,
                initClique,
                addition
            );
            System.out.println("Using Scale-Free network (Barabási-Albert): initClique=" + initClique + ", addition=" + addition);
        } else {
            // Default: RANDOM network
            this.config = new RandomNetworkConfiguration(
                numAgentsConfig,
                domainSize,
                minCost,
                maxCost,
                networkDensity
            );
            System.out.println("Using Random network: density=" + networkDensity);
        }
        
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
            
            if (selectedAlgorithm == AlgorithmType.MAXSUM || selectedAlgorithm == AlgorithmType.PMAXSUM) {
                System.out.println("Last round: " + lastRound);
            }
        } catch (Exception e) {
            // Default to BASIC if not specified or error
            System.out.println("Could not read algorithm config, defaulting to BASIC. Error: " + e.getMessage());
            this.selectedAlgorithm = AlgorithmType.BASIC;
        }
    }
    
    /**
     * Determines if the simulation has terminated.
     * Delegates to round-based or timeout-based halting depending on configuration.
     */
    @Override
    public boolean hasTerminated() {
        if (!iterationInProgress) {
            return false; // Not running an iteration
        }
        
        // Check termination condition based on mode
        if (lastRound > 0) {
            return checkRoundBasedHalting();
        } else {
            return checkTimeoutBasedHalting();
        }
    }
    
    /**
     * Checks if all agents have reached the configured last round.
     * 
     * @return false (simulation continues for next iteration or keeps running)
     */
    private boolean checkRoundBasedHalting() {
        boolean allAgentsReady = true;
        int minRound = Integer.MAX_VALUE;
        
        Enumeration<?> nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            int agentId = agent.getID();
            
            // Only check actual agents (ID 1 to N), skip function nodes
            if (agentId >= 1 && agentId <= numAgents) {
                int currentRound = agent.getProperty("rounds");
                minRound = Math.min(minRound, currentRound);
                if (currentRound < lastRound) {
                    allAgentsReady = false;
                }
            }
        }
        
        if (allAgentsReady && minRound >= lastRound) {
            System.out.println("All agents reached round " + minRound + " (last round: " + lastRound + ") - halting");
            finishCurrentIteration();
        }
        
        return false; // Continue simulation for next iteration
    }
    
    /**
     * Checks if the iteration timeout has been reached.
     * 
     * @return false (simulation continues for next iteration or keeps running)
     */
    private boolean checkTimeoutBasedHalting() {
        long elapsed = System.currentTimeMillis() - iterationStartTime;
        if (elapsed >= iterationTimeoutMs) {
            finishCurrentIteration();
        }
        
        return false; // Continue simulation for next iteration
    }
    
    /**
     * Get agent by DCOP ID (1-based).
     * 
     * @param agentId The agent ID (1 to N)
     * @return The agent, or null if not found
     */
    private IDCOPAgent getAgentById(int agentId) {
        Enumeration<?> nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            if (agent.getID() == agentId) {
                return agent;
            }
        }
        return null;
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
            case PMAXSUM:
                return new PMaxSumNetworkBuilder(algorithmSeed, lastRound);
            case MAXSUM:
                return new MaxSumNetworkBuilder(algorithmSeed, lastRound);
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
     * Custom button to start DCOP testing with P-MAXSUM algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (P-MAXSUM)", toolTipText="Start DCOP test with privacy-preserving P-MAXSUM algorithm (Paillier)")
    public void runPMaxSumTest() {
        selectedAlgorithm = AlgorithmType.PMAXSUM;
        runDCOPTest();
    }
    
    /**
     * Custom button to start DCOP testing with vanilla Max-Sum algorithm.
     */
    @CustomButton(buttonText="Run DCOP Test (MAXSUM)", toolTipText="Start DCOP test with vanilla Max-Sum algorithm (no privacy)")
    public void runMaxSumTest() {
        selectedAlgorithm = AlgorithmType.MAXSUM;
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
        totalIterations = this.numIterations;
        results = new TestResults(config);
        iterationActualRuntimes = new long[totalIterations];
        
        // Set up TestConfig for output
        TestResults.TestConfig testConfig = new TestResults.TestConfig();
        testConfig.algorithm = selectedAlgorithm.name();
        testConfig.networkType = networkTypeConfig.name();
        testConfig.numAgents = numAgents;
        testConfig.domainSize = domainSizeConfig;
        testConfig.timeoutSec = (int)(timeoutMs / 1000);
        testConfig.numProblems = totalIterations;
        testConfig.minCost = minCostConfig;
        testConfig.maxCost = maxCostConfig;
        testConfig.networkDensity = networkDensityConfig;
        testConfig.initClique = initCliqueConfig;
        testConfig.addition = additionConfig;
        testConfig.problemSeed = problemSeed;
        results.setTestConfig(testConfig);
        
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
        long iterProblemSeed = this.problemSeed + currentIteration;
        long iterAlgorithmSeed = this.algorithmSeed + currentIteration;
        
        // Store current problem seed for results tracking
        this.currentProblemSeed = iterProblemSeed;
        
        // Generate new DCOP problem using configuration
        currentProblem = config.generateProblem(iterProblemSeed);
        System.out.println("Generated problem: " + currentProblem);
        
        // Build network from problem using selected algorithm
        currentNetworkBuilder = createNetworkBuilder(selectedAlgorithm, iterAlgorithmSeed);
        DCOPNetwork network = currentNetworkBuilder.buildNetwork(currentProblem);
        System.out.println("Built network: " + network + " with " + selectedAlgorithm + " algorithm");
        
        // Deploy network to Sinalgo runtime
        deployNetwork(network);

     
        // TODO: REFACTOR - This two-phase wiring (buildNetwork + wireNetwork) is needed because
        // Sinalgo assigns node IDs during deployment, not during node creation. Consider:
        // 1. Moving wireNetwork() logic into the network builders
        // 2. Having network builders create Sinalgo edges directly (not just in deployNetwork)
        // 3. For Max-Sum, only create agent↔function edges instead of full mesh
        // See also: deployNetwork() full mesh creation which could be algorithm-specific
        
        // Special handling for MAXSUM/P-MAXSUM: wire brains to Sinalgo node IDs after deployment
        if (selectedAlgorithm == AlgorithmType.PMAXSUM && currentNetworkBuilder instanceof PMaxSumNetworkBuilder) {
            PMaxSumNetworkBuilder pmaxsumBuilder = (PMaxSumNetworkBuilder) currentNetworkBuilder;
            java.util.Map<Integer, Integer> nodeIdMap = new java.util.HashMap<>();
            int idx = 0;
            for (IDCOPAgent agent : network) {
                Node node = (Node) agent;
                nodeIdMap.put(idx, node.ID);
                idx++;
            }
            pmaxsumBuilder.wireNetwork(currentProblem, nodeIdMap);
            System.out.println("Wired P-MAXSUM brains to Sinalgo node IDs");
        } else if (selectedAlgorithm == AlgorithmType.MAXSUM && currentNetworkBuilder instanceof MaxSumNetworkBuilder) {
            MaxSumNetworkBuilder maxsumBuilder = (MaxSumNetworkBuilder) currentNetworkBuilder;
            java.util.Map<Integer, Integer> nodeIdMap = new java.util.HashMap<>();
            int idx = 0;
            for (IDCOPAgent agent : network) {
                Node node = (Node) agent;
                nodeIdMap.put(idx, node.ID);
                idx++;
            }
            maxsumBuilder.wireNetwork(currentProblem, nodeIdMap);
            System.out.println("Wired MAXSUM brains to Sinalgo node IDs");
        }
        
        // Start all agents
        Enumeration<?> nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            agent.startAlgorithm();
        }
        
        // Set up iteration state - start timer immediately
        iterationTimeoutMs = this.timeoutMs;
        iterationInProgress = true;
        iterationStartTime = System.currentTimeMillis();  // Start timer NOW

        
        if (lastRound > 0) {
            System.out.println("Running iteration until agent 1 completes last round (" + lastRound + ")...");
        } else {
            System.out.println("Running iteration for up to " + (iterationTimeoutMs / 1000) + " seconds...");
        }
        
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
        
        // Collect agent values (only from actual agents, skip function nodes for P-MAXSUM)
        int[] agentValues = new int[numAgents + 1]; // Size N+1, index 0 unused
        nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            int agentId = agent.getID();
            // Only collect from agent nodes (ID 1 to N), skip function nodes
            if (agentId >= 1 && agentId <= numAgents) {
                agentValues[agentId] = agent.getSelectedValue();
            }
        }
        
        // Calculate total cost
        int totalCost = currentProblem.getTotalCost(agentValues);
        
        // Collect round statistics only from actual agents
        int minRounds = Integer.MAX_VALUE;
        int maxRoundsActual = 0;
        int totalRounds = 0;
        int agentsWithRounds = 0;
        nodeEnum = Tools.getNodeList().getNodeEnumeration();
        while (nodeEnum.hasMoreElements()) {
            IDCOPAgent agent = (IDCOPAgent) nodeEnum.nextElement();
            int agentId = agent.getID();
            // Only collect from agent nodes (ID 1 to N), skip function nodes
            if (agentId >= 1 && agentId <= numAgents) {
                int rounds = agent.getProperty("rounds");
                if (rounds >= 0) {  // Only count if supported
                    totalRounds += rounds;
                    minRounds = Math.min(minRounds, rounds);
                    maxRoundsActual = Math.max(maxRoundsActual, rounds);
                    agentsWithRounds++;
                }
            }
        }
        
        // Store result (use 1-based iteration number for display)
        // If no agents reported rounds, use -1 to indicate algorithm doesn't use rounds
        int maxRoundsForResult = (agentsWithRounds > 0) ? maxRoundsActual : -1;
        
        // Pass problem for export if enabled (otherwise pass null to save memory)
        DCOPProblem problemToStore = exportProblems ? currentProblem : null;
        results.addResult(currentIteration + 1, currentProblemSeed, actualRuntime, totalCost, 
                          maxRoundsForResult, agentValues, problemToStore);
        
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
            // Create results and logs directories if they don't exist
            File resultsDir = new File("results");
            if (!resultsDir.exists()) {
                resultsDir.mkdirs();
            }
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            results.printSummary();
            
            // Determine output filenames
            String resultsFilename;
            String problemsFilename = null;
            
            if (outputPrefix != null && !outputPrefix.isEmpty()) {
                // Use prefix-based naming for structured output
                resultsFilename = TestResults.generateFilename(outputPrefix, "results");
                if (exportProblems) {
                    problemsFilename = TestResults.generateFilename(outputPrefix, "problems");
                }
                // Write with config header
                results.writeResultsWithConfig(resultsFilename);
            } else {
                // Legacy naming
                resultsFilename = TestResults.generateTimestampedFilename(selectedAlgorithm.name());
                results.writeToCSV(resultsFilename);
            }
            
            // Export problems if enabled
            if (exportProblems && problemsFilename != null) {
                results.writeProblemsToCSV(problemsFilename);
            }
            
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
                                  resultsFilename),
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
