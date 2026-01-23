package dcop.algorithms.basic;

import java.util.ArrayList;
import java.util.List;
import dcop.common.DCOPProblem;
import dcop.common.network.DCOPNetwork;
import dcop.common.network.IDCOPNetworkBuilder;
import dcop.common.nodes.IDCOPAgent;
import dcop.algorithms.basic.sinalgo.nodes.BasicDCOPAgent;

/**
 * Builds a DCOP network using the Basic (random) algorithm agents.
 * Creates BasicDCOPAgent instances but does not add them to the Sinalgo runtime.
 */
public class BasicNetworkBuilder implements IDCOPNetworkBuilder {
    
    private final long algorithmSeed;
    
    /**
     * Creates a Basic network builder with specified configuration.
     * 
     * @param algorithmSeed The seed for algorithm randomization
     */
    public BasicNetworkBuilder(long algorithmSeed) {
        this.algorithmSeed = algorithmSeed;
    }
    
    /**
     * Build a DCOP network from a problem instance using Basic algorithm agents.
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
        
        // Create N Basic algorithm agents
        for (int i = 0; i < numAgents; i++) {
            BasicDCOPAgent agent = new BasicDCOPAgent();
            agent.setDomainSize(domainSize);
            agent.setAlgorithmSeed(algorithmSeed);
            // Note: Position is NOT set here - will be set during deployment
            // Note: finishInitializationWithDefaultModels NOT called - deployment does this
            
            agents.add(agent);
        }
        
        return new DCOPNetwork(problem, agents);
    }
}
