package dcop.algorithms.dsa;

import java.util.ArrayList;
import java.util.List;
import dcop.common.DCOPProblem;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;
import dcop.algorithms.dsa.sinalgo.nodes.DSAAgent;

/**
 * Builds a DCOP network using the DSA (Distributed Stochastic Algorithm) agents.
 * Creates DSAAgent instances with configured stochastic parameter.
 */
public class DSANetworkBuilder implements IDCOPNetworkBuilder {
    
    private final long algorithmSeed;
    private final double stochastic;
    private int maxRounds = -1;  // -1 = unlimited, 0 = no rounds, >0 = limit

    /**
     * Creates a DSA network builder with specified configuration.
     * 
     * @param algorithmSeed The seed for algorithm randomization
     * @param stochastic Probability of changing to better value (0.0 to 1.0)
     */
    public DSANetworkBuilder(long algorithmSeed, double stochastic) {
        this.algorithmSeed = algorithmSeed;
        this.stochastic = stochastic;
    }
    
    /**
     * Build a DCOP network from a problem instance using DSA algorithm agents.
     * Creates all agent objects with proper configuration.
     * Agents are stored with 0-based indexing (indices 0 to N-1).
     * 
     * @param problem The DCOP problem
     * @return A DCOPNetwork ready for deployment
     */
    @Override
    public DCOPNetwork buildNetwork(DCOPProblem problem) {
        int numAgents = problem.getNumAgents();
        int domainSize = problem.getDomainSize();
        
        // Create list with size N (0-based indexing)
        List<IDCOPAgent> agents = new ArrayList<>(numAgents);
        
        // Create N DSA algorithm agents
        for (int i = 0; i < numAgents; i++) {
            DSAAgent agent = new DSAAgent();
            agent.setDomainSize(domainSize);
            agent.setAlgorithmSeed(algorithmSeed);
            agent.setStochastic(stochastic);
            agent.setProblem(problem);  // DSA needs problem reference for cost calculation
            agent.setMaxRounds(maxRounds);  // Set round limit
            // Note: Position is NOT set here - will be set during deployment
            // Note: finishInitializationWithDefaultModels NOT called - deployment does this
            
            agents.add(agent);
        }
        
        return new DCOPNetwork(problem, agents);
    }
}
