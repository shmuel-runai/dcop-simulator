package dcop.common.network;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import dcop.common.nodes.IDCOPAgent;
import dcop.common.DCOPProblem;

/**
 * Represents a DCOP network ready to be deployed.
 * Contains all agent objects and topology information.
 * 
 * Uses 0-based internal indexing. Agent IDs (1 to N) are mapped
 * to list indices (0 to N-1) internally.
 * 
 * Implements Iterable for easy for-each loops over agents.
 * 
 * Works with any agent implementation via the IDCOPAgent interface,
 * allowing different DCOP algorithms to use the same network infrastructure.
 */
public class DCOPNetwork implements Iterable<IDCOPAgent> {
    
    private final DCOPProblem problem;
    private final List<IDCOPAgent> agents; // 0-based: indices 0 to N-1
    
    /**
     * Package-private constructor (only NetworkBuilder should create this).
     * 
     * @param problem The DCOP problem this network represents
     * @param agents List of agents (0-based, indices 0 to N-1)
     */
    public DCOPNetwork(DCOPProblem problem, List<IDCOPAgent> agents) {
        this.problem = problem;
        this.agents = agents;
    }
    
    /**
     * Get the agent with the specified ID (1-based, matching Sinalgo IDs).
     * Internally converts to 0-based index.
     * 
     * @param agentID The agent ID (1 to N)
     * @return The agent, or null if ID is out of range
     */
    public IDCOPAgent getAgent(int agentID) {
        int index = agentID - 1; // Convert 1-based ID to 0-based index
        if (index < 0 || index >= agents.size()) {
            return null;
        }
        return agents.get(index);
    }
    
    /**
     * Get all agents as a list.
     * 
     * @return List of agents (indices 0 to N-1)
     */
    public List<IDCOPAgent> getAgents() {
        return agents;
    }
    
    /**
     * Get the number of agents in this network.
     * 
     * @return Number of agents
     */
    public int getNumAgents() {
        return agents.size();
    }
    
    /**
     * Get the DCOP problem this network represents.
     * 
     * @return The DCOP problem
     */
    public DCOPProblem getProblem() {
        return problem;
    }
    
    /**
     * Get the IDs of neighbors for a given agent.
     * 
     * @param agentID The agent ID (1-based)
     * @return List of neighbor IDs
     */
    public List<Integer> getNeighbors(int agentID) {
        return problem.getNeighbors(agentID);
    }
    
    /**
     * Check if two agents should be connected.
     * 
     * @param agentID1 First agent ID (1-based)
     * @param agentID2 Second agent ID (1-based)
     * @return true if agents should be connected
     */
    public boolean hasEdge(int agentID1, int agentID2) {
        return problem.isConnected(agentID1, agentID2);
    }
    
    /**
     * Returns an iterator over agents.
     * Enables for-each loops: for (IDCOPAgent agent : network) { ... }
     */
    @Override
    public Iterator<IDCOPAgent> iterator() {
        return agents.iterator();
    }
    
    /**
     * Returns a stream of agents for functional operations.
     */
    public Stream<IDCOPAgent> stream() {
        return agents.stream();
    }
    
    @Override
    public String toString() {
        return String.format("DCOPNetwork[agents=%d, edges=%d]", 
            agents.size(), problem.getNumEdges());
    }
}
