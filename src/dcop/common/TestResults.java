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
 * Works with any simulation configuration that implements ISimulationConfiguration,
 * allowing flexibility for different network topologies and configurations.
 */
public class TestResults {
    
    /**
     * Represents results from a single simulation iteration.
     */
    public static class IterationResult {
        public final int iterationNum;
        public final long runtimeMs;
        public final int totalCost;
        public final int maxRounds;  // Max rounds across agents, -1 if algorithm doesn't use rounds
        public final int[] agentValues; // Size N+1, index 0 unused
        
        public IterationResult(int iterationNum, long runtimeMs, int totalCost, int maxRounds, int[] agentValues) {
            this.iterationNum = iterationNum;
            this.runtimeMs = runtimeMs;
            this.totalCost = totalCost;
            this.maxRounds = maxRounds;
            this.agentValues = agentValues.clone(); // Defensive copy
        }
    }
    
    private final List<IterationResult> results;
    private final ISimulationConfiguration config;
    
    /**
     * Creates a new TestResults object.
     * 
     * @param config The simulation configuration used for these results
     */
    public TestResults(ISimulationConfiguration config) {
        this.results = new ArrayList<>();
        this.config = config;
    }
    
    /**
     * Adds a result from one iteration.
     * 
     * @param iteration Iteration number (1-based)
     * @param runtimeMs Runtime in milliseconds
     * @param totalCost Total system cost
     * @param maxRounds Max rounds across agents, -1 if algorithm doesn't use rounds
     * @param agentValues Agent value assignments (size N+1, index 0 unused)
     */
    public void addResult(int iteration, long runtimeMs, int totalCost, int maxRounds, int[] agentValues) {
        results.add(new IterationResult(iteration, runtimeMs, totalCost, maxRounds, agentValues));
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
     * Writes results to a CSV file and prints confirmation message.
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
     * @return Number of iterations recorded
     */
    public int getNumResults() {
        return results.size();
    }
}
