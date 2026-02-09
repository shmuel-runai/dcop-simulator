package dcop.common;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import dcop.common.configuration.ISimulationConfiguration;

/**
 * Stores and manages results from multiple DCOP simulation iterations.
 * Provides methods for calculating statistics and exporting results.
 * 
 * Supports two output files:
 * 1. Results CSV: Configuration header + per-problem results (cost, rounds, runtime)
 * 2. Problems CSV: DCOP problem structures (edges, cost matrices) for verification
 * 
 * Works with any simulation configuration that implements ISimulationConfiguration,
 * allowing flexibility for different network topologies and configurations.
 */
public class TestResults {
    
    /**
     * Represents results from a single simulation iteration.
     */
    public static class IterationResult {
        public final int iterationNum;
        public final long problemSeed;
        public final long runtimeMs;
        public final int totalCost;
        public final int maxRounds;  // Max rounds across agents, -1 if algorithm doesn't use rounds
        public final int[] agentValues; // Size N+1, index 0 unused
        public final DCOPProblem problem; // The problem instance (for export)
        
        public IterationResult(int iterationNum, long problemSeed, long runtimeMs, 
                              int totalCost, int maxRounds, int[] agentValues, DCOPProblem problem) {
            this.iterationNum = iterationNum;
            this.problemSeed = problemSeed;
            this.runtimeMs = runtimeMs;
            this.totalCost = totalCost;
            this.maxRounds = maxRounds;
            this.agentValues = agentValues.clone(); // Defensive copy
            this.problem = problem;
        }
    }
    
    /**
     * Test configuration metadata for output.
     */
    public static class TestConfig {
        public String algorithm;
        public String networkType;
        public int numAgents;
        public int domainSize;
        public int timeoutSec;
        public int numProblems;
        public int minCost;
        public int maxCost;
        public double networkDensity;  // For RANDOM
        public int initClique;         // For SCALE_FREE
        public int addition;           // For SCALE_FREE
        public long problemSeed;       // Base seed
        
        public TestConfig() {
            // Defaults
            this.algorithm = "UNKNOWN";
            this.networkType = "RANDOM";
            this.numAgents = 10;
            this.domainSize = 5;
            this.timeoutSec = 60;
            this.numProblems = 10;
            this.minCost = 0;
            this.maxCost = 10;
            this.networkDensity = 0.3;
            this.initClique = 4;
            this.addition = 2;
            this.problemSeed = 1000;
        }
        
        public String toCSVHeader() {
            return "algorithm,network_type,num_agents,domain_size,timeout_sec,num_problems,min_cost,max_cost,network_density,init_clique,addition,problem_seed";
        }
        
        public String toCSVRow() {
            String density = "RANDOM".equals(networkType) ? String.valueOf(networkDensity) : "";
            String clique = "SCALE_FREE".equals(networkType) ? String.valueOf(initClique) : "";
            String add = "SCALE_FREE".equals(networkType) ? String.valueOf(addition) : "";
            return String.format("%s,%s,%d,%d,%d,%d,%d,%d,%s,%s,%s,%d",
                algorithm, networkType, numAgents, domainSize, timeoutSec, numProblems,
                minCost, maxCost, density, clique, add, problemSeed);
        }
    }
    
    private final List<IterationResult> results;
    private final ISimulationConfiguration config;
    private TestConfig testConfig;
    
    /**
     * Creates a new TestResults object.
     * 
     * @param config The simulation configuration used for these results
     */
    public TestResults(ISimulationConfiguration config) {
        this.results = new ArrayList<>();
        this.config = config;
        this.testConfig = new TestConfig();
    }
    
    /**
     * Sets the test configuration metadata.
     * 
     * @param testConfig Configuration to use for output headers
     */
    public void setTestConfig(TestConfig testConfig) {
        this.testConfig = testConfig;
    }
    
    /**
     * Gets the test configuration.
     * 
     * @return The test configuration
     */
    public TestConfig getTestConfig() {
        return testConfig;
    }
    
    /**
     * Adds a result from one iteration (legacy method, no problem tracking).
     * 
     * @param iteration Iteration number (1-based)
     * @param runtimeMs Runtime in milliseconds
     * @param totalCost Total system cost
     * @param maxRounds Max rounds across agents, -1 if algorithm doesn't use rounds
     * @param agentValues Agent value assignments (size N+1, index 0 unused)
     */
    public void addResult(int iteration, long runtimeMs, int totalCost, int maxRounds, int[] agentValues) {
        addResult(iteration, 0, runtimeMs, totalCost, maxRounds, agentValues, null);
    }
    
    /**
     * Adds a result from one iteration with problem tracking.
     * 
     * @param iteration Iteration number (1-based)
     * @param problemSeed Seed used to generate this problem
     * @param runtimeMs Runtime in milliseconds
     * @param totalCost Total system cost
     * @param maxRounds Max rounds across agents, -1 if algorithm doesn't use rounds
     * @param agentValues Agent value assignments (size N+1, index 0 unused)
     * @param problem The DCOP problem instance (for export, can be null)
     */
    public void addResult(int iteration, long problemSeed, long runtimeMs, int totalCost, 
                          int maxRounds, int[] agentValues, DCOPProblem problem) {
        results.add(new IterationResult(iteration, problemSeed, runtimeMs, totalCost, maxRounds, agentValues, problem));
    }
    
    /**
     * Calculates the average cost across all iterations.
     * 
     * @return Average cost, or 0.0 if no results
     */
    public double getAverageCost() {
        if (results.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (IterationResult r : results) {
            sum += r.totalCost;
        }
        return (double) sum / results.size();
    }
    
    /**
     * Calculates the average runtime across all iterations.
     * 
     * @return Average runtime in milliseconds, or 0.0 if no results
     */
    public double getAverageRuntime() {
        if (results.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (IterationResult r : results) {
            sum += r.runtimeMs;
        }
        return (double) sum / results.size();
    }
    
    /**
     * Writes results to a CSV file (legacy format, no config header).
     * 
     * @param filename Path to output CSV file
     * @throws IOException If file cannot be written
     */
    public void writeToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("Iteration,Runtime_ms,TotalCost,MaxRounds,AgentValues");
            
            // Write data rows
            for (IterationResult result : results) {
                writer.print(result.iterationNum);
                writer.print(",");
                writer.print(result.runtimeMs);
                writer.print(",");
                writer.print(result.totalCost);
                writer.print(",");
                writer.print(result.maxRounds);
                writer.print(",\"");
                writer.print(Arrays.toString(result.agentValues));
                writer.println("\"");
            }
        }
        System.out.println("Results saved to: " + filename);
    }
    
    /**
     * Writes results to a CSV file with configuration header.
     * Format:
     * - Line 1: "# Configuration"
     * - Line 2: Config header
     * - Line 3: Config values
     * - Line 4: Empty
     * - Line 5: "# Results"
     * - Line 6: Results header
     * - Lines 7+: Results data
     * 
     * @param filename Path to output CSV file
     * @throws IOException If file cannot be written
     */
    public void writeResultsWithConfig(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Configuration section
            writer.println("# Configuration");
            writer.println(testConfig.toCSVHeader());
            writer.println(testConfig.toCSVRow());
            writer.println();
            
            // Results section
            writer.println("# Results");
            writer.println("problem_id,seed,final_cost,rounds_completed,runtime_ms");
            
            for (IterationResult result : results) {
                writer.print(result.iterationNum);
                writer.print(",");
                writer.print(result.problemSeed);
                writer.print(",");
                writer.print(result.totalCost);
                writer.print(",");
                writer.print(result.maxRounds);
                writer.print(",");
                writer.println(result.runtimeMs);
            }
        }
        System.out.println("Results with config saved to: " + filename);
    }
    
    /**
     * Writes DCOP problem structures to a separate CSV file.
     * This file contains the actual cost matrices for verification.
     * 
     * @param filename Path to output CSV file
     * @throws IOException If file cannot be written
     */
    public void writeProblemsToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header
            writer.println("problem_id,seed,num_agents,domain_size,num_edges,edges,cost_matrices");
            
            for (IterationResult result : results) {
                if (result.problem == null) {
                    continue; // Skip if no problem recorded
                }
                
                DCOPProblem p = result.problem;
                writer.print(result.iterationNum);
                writer.print(",");
                writer.print(result.problemSeed);
                writer.print(",");
                writer.print(p.getNumAgents());
                writer.print(",");
                writer.print(p.getDomainSize());
                writer.print(",");
                writer.print(p.getNumEdges());
                writer.print(",\"");
                
                // Edges list
                StringBuilder edges = new StringBuilder("[");
                StringBuilder matrices = new StringBuilder("[");
                boolean first = true;
                
                for (int i = 1; i <= p.getNumAgents(); i++) {
                    for (int j : p.getNeighbors(i)) {
                        if (i < j) { // Only output each edge once
                            if (!first) {
                                edges.append(",");
                                matrices.append(",");
                            }
                            first = false;
                            
                            edges.append("(").append(i).append(",").append(j).append(")");
                            
                            // Cost matrix for this edge
                            int[][] costMatrix = p.getCostMatrix(i, j);
                            matrices.append(matrixToString(costMatrix));
                        }
                    }
                }
                edges.append("]");
                matrices.append("]");
                
                writer.print(edges.toString());
                writer.print("\",\"");
                writer.print(matrices.toString());
                writer.println("\"");
            }
        }
        System.out.println("Problems saved to: " + filename);
    }
    
    /**
     * Converts a 2D matrix to a string representation.
     */
    private String matrixToString(int[][] matrix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("[");
            for (int j = 0; j < matrix[i].length; j++) {
                if (j > 0) sb.append(",");
                sb.append(matrix[i][j]);
            }
            sb.append("]");
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Prints a summary of results to console.
     */
    public void printSummary() {
        System.out.println("\n=== DCOP Simulation Results ===");
        System.out.println("Configuration: " + config.toString());
        System.out.println("Iterations: " + results.size());
        System.out.println();
        
        for (IterationResult result : results) {
            String roundsStr = result.maxRounds >= 0 ? String.valueOf(result.maxRounds) : "N/A";
            System.out.println("Iteration " + result.iterationNum + 
                               ": Cost=" + result.totalCost + 
                               ", Rounds=" + roundsStr +
                               ", Runtime=" + result.runtimeMs + "ms");
        }
        
        System.out.println();
        System.out.printf("Average Cost: %.1f\n", getAverageCost());
        System.out.printf("Average Runtime: %.1fms\n", getAverageRuntime());
    }
    
    /**
     * Generates a timestamped filename for CSV output.
     * 
     * @param algorithmName Name of the algorithm (e.g., "PMGM", "PDSA", "BASIC")
     * @return Filename like "logs/dcop_results_PMGM_20250126_143022.csv"
     */
    public static String generateTimestampedFilename(String algorithmName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        return "logs/dcop_results_" + algorithmName + "_" + timestamp + ".csv";
    }
    
    /**
     * Generates a filename with custom prefix.
     * 
     * @param prefix Custom prefix for the filename
     * @param suffix Suffix type (e.g., "results", "problems")
     * @return Filename like "results/test_PREFIX_results.csv"
     */
    public static String generateFilename(String prefix, String suffix) {
        return "results/test_" + prefix + "_" + suffix + ".csv";
    }
    
    /**
     * @return Number of iterations recorded
     */
    public int getNumResults() {
        return results.size();
    }
    
    /**
     * @return List of all iteration results (for external processing)
     */
    public List<IterationResult> getResults() {
        return new ArrayList<>(results);
    }
}
