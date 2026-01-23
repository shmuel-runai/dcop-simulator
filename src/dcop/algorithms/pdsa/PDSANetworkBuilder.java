package dcop.algorithms.pdsa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dcop.algorithms.pdsa.sinalgo.nodes.PDSAAgent;
import dcop.common.DCOPProblem;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.ShareGenerator;

/**
 * Builds a DCOP network using the PDSA (Private Distributed Stochastic Algorithm) agents.
 * 
 * Key differences from DSA:
 * - Creates full mesh network topology (all agents connected)
 * - Agents receive only local constraint matrices (privacy)
 * - Pre-distributes r-key and bit shares for MPC operations
 * - Uses prime 2^31 - 1 for modular arithmetic
 */
public class PDSANetworkBuilder implements IDCOPNetworkBuilder {
    
    private static final long PRIME = 2147483647L; // 2^31 - 1 (Mersenne prime)
    private static final int NUM_BITS = 31; // Number of bits for r-key breakdown
    
    private final long algorithmSeed;
    private final double stochastic;
    private int maxRounds = -1;  // -1 = unlimited, 0 = no rounds, >0 = limit
    
    /**
     * Creates a PDSA network builder with specified configuration.
     * 
     * @param algorithmSeed The seed for algorithm randomization
     * @param stochastic Probability of changing to better value (0.0 to 1.0)
     */
    public PDSANetworkBuilder(long algorithmSeed, double stochastic) {
        this.algorithmSeed = algorithmSeed;
        this.stochastic = stochastic;
    }
    
    /**
     * Build a PDSA network from a problem instance.
     * Creates all agent objects with proper configuration, including:
     * - Local constraint matrices (real + dummy zero matrices)
     * - Pre-distributed r-key and bit shares for MPC
     * - Full mesh network topology
     * 
     * @param problem The DCOP problem
     * @return A DCOPNetwork ready for deployment
     */
    @Override
    public DCOPNetwork buildNetwork(DCOPProblem problem) {
        int numAgents = problem.getNumAgents();
        int domainSize = problem.getDomainSize();
        
        // Create full mesh problem FIRST for deployment
        // (Contains real costs for connected pairs, zero costs for others)
        DCOPProblem fullMesh = createFullMeshProblem(problem);
        
        // Create agents
        List<IDCOPAgent> agents = new ArrayList<>(numAgents);
        
        for (int agentID : problem) {
            PDSAAgent agent = new PDSAAgent();
            agent.setDomainSize(domainSize);
            agent.setAlgorithmSeed(algorithmSeed);
            agent.setStochastic(stochastic);
            agent.setPrime(PRIME);
            agent.setMaxRounds(maxRounds);
            // Use fullMesh to get constraint matrices (real costs or zeros)
            agent.setConstraintMatrices(buildLocalConstraints(agentID, fullMesh));
            agents.add(agent);
        }
        
        // Generate and distribute secrets (r-key and bits) directly to agents
        distributeSecrets(agents, numAgents);
        
        // Return network with FULL MESH problem (for deployment)
        return new DCOPNetwork(fullMesh, agents);
    }
    
    /**
     * Builds local constraint matrices for a specific agent.
     * Gets cost matrices from fullMesh (which has real costs or zeros for all pairs).
     * 
     * @param agentID The agent ID (1 to N)
     * @param fullMesh The full mesh problem with all constraints
     * @return Map from neighbor ID to M x M cost matrix
     */
    private Map<Integer, int[][]> buildLocalConstraints(int agentID, DCOPProblem fullMesh) {
        Map<Integer, int[][]> local = new HashMap<>();
        
        for (int neighborID : fullMesh) {
            if (neighborID == agentID) continue; // Skip self
            
            // Get cost matrix from fullMesh (real costs or zero matrix)
            local.put(neighborID, fullMesh.getCostMatrix(agentID, neighborID));
        }
        
        return local;
    }
    
    /**
     * Generates and distributes r-key and bit shares directly to all agents.
     * Shares are stored as sticky (persist forever, never cleaned up).
     * 
     * @param agents List of agents (0-based indexing, but agent IDs are 1-based)
     * @param numAgents Number of agents
     */
    private void distributeSecrets(List<IDCOPAgent> agents, int numAgents) {
        Random rng = new Random(algorithmSeed);
        int threshold = numAgents / 2; // Shamir threshold: N/2
        
        // Generate r-key value
        long rKeyValue = Math.abs(rng.nextLong()) % PRIME;
        
        // Create share generator for r-key
        ShareGenerator rKeyGen = new ShareGenerator(rKeyValue, threshold, PRIME, rng);
        
        // Create share generators for each bit of r-key
        ShareGenerator[] bitGens = new ShareGenerator[NUM_BITS];
        for (int bitIndex = 0; bitIndex < NUM_BITS; bitIndex++) {
            long bitValue = (rKeyValue >> bitIndex) & 1L;
            bitGens[bitIndex] = new ShareGenerator(bitValue, threshold, PRIME, rng);
        }
        
        // Distribute shares directly to each agent
        for (int i = 0; i < numAgents; i++) {
            PDSAAgent agent = (PDSAAgent) agents.get(i);
            IShareStorage storage = agent.getShareStorage();
            int agentID = i + 1; // Agent IDs are 1-based
            
            // Generate and store r-key share directly
            storage.storeStickyShare("r-key", rKeyGen.generateShare(agentID));
            
            // Generate and store bit shares directly
            for (int bitIndex = 0; bitIndex < NUM_BITS; bitIndex++) {
                String bitId = "r-key[" + bitIndex + "]";
                storage.storeStickyShare(bitId, bitGens[bitIndex].generateShare(agentID));
            }
        }
    }
    
    /**
     * Creates a full mesh DCOP problem from the original problem.
     * All agent pairs are connected with either real or dummy zero-cost constraints.
     * 
     * @param original The original DCOP problem
     * @return A new DCOPProblem with full mesh connectivity
     */
    private DCOPProblem createFullMeshProblem(DCOPProblem original) {
        int numAgents = original.getNumAgents();
        int domainSize = original.getDomainSize();
        DCOPProblem fullMesh = new DCOPProblem(numAgents, domainSize);
        
        // Add constraints for ALL pairs (full mesh)
        // Use getCostMatrix which returns real costs or zero matrix
        for (int i : original) {
            for (int j : original) {
                if (i < j) { // Only add each pair once
                    fullMesh.addConstraint(i, j, original.getCostMatrix(i, j));
                }
            }
        }
        
        return fullMesh;
    }
}
