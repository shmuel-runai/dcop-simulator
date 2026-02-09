package dcop.algorithms.pmaxsum.sinalgo;

import java.awt.Color;
import java.awt.Graphics;

import dcop.algorithms.pmaxsum.IMaxSumBrain;
import dcop.algorithms.pmaxsum.INodeTransport;
import dcop.common.nodes.IDCOPAgent;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;

/**
 * Generic Sinalgo node for P-MAXSUM algorithm.
 * 
 * Works with both AgentBrain and FunctionBrain through the IMaxSumBrain interface.
 * This is the ONLY Sinalgo-specific class for nodes - all algorithm logic
 * lives in the brain implementations.
 * 
 * Design:
 * - Single node class for both agents and function nodes
 * - Brain is injected after construction via setBrain()
 * - Implements IDCOPAgent for simulation framework compatibility
 * - Implements INodeTransport for brain's message sending needs
 */
public class PMaxSumNode extends Node implements IDCOPAgent, INodeTransport {
    
    // The brain that contains all algorithm logic
    private IMaxSumBrain brain;
    
    // DCOP agent index (1..N for agents, negative for functions)
    private int dcopIndex;
    
    // Domain size (for agents only)
    private int domainSize;
    
    /**
     * Default constructor required by Sinalgo.
     */
    public PMaxSumNode() {
        this.dcopIndex = -1;
        this.domainSize = 5;
    }
    
    // ========== Brain injection ==========
    
    /**
     * Set the brain for this node.
     * Called by NetworkBuilder after node construction.
     * 
     * @param brain The AgentBrain or FunctionBrain
     */
    public void setBrain(IMaxSumBrain brain) {
        this.brain = brain;
    }
    
    /**
     * Get the brain.
     * 
     * @return The brain, or null if not set
     */
    public IMaxSumBrain getBrain() {
        return brain;
    }
    
    /**
     * Set the DCOP index for this node.
     * For agents: 1 to N (matches agent ID)
     * For functions: negative (e.g., -1001003 for function(1,3))
     * 
     * @param index The DCOP index
     */
    public void setDcopIndex(int index) {
        this.dcopIndex = index;
    }
    
    // ========== IDCOPAgent implementation ==========
    
    @Override
    public int getID() {
        // Return DCOP agent ID for agents, Sinalgo ID for functions
        return (brain != null && brain.isAgent()) ? dcopIndex : this.ID;
    }
    
    @Override
    public int getSelectedValue() {
        return (brain != null) ? brain.getAssignment() : -1;
    }
    
    @Override
    public void startAlgorithm() {
        if (brain != null) {
            brain.start();
        }
    }
    
    @Override
    public void stopAlgorithm() {
        // Nothing to do - brain handles its own state
    }
    
    @Override
    public boolean isActive() {
        return brain != null && !brain.isDone();
    }
    
    @Override
    public int getProperty(String property) {
        if ("rounds".equals(property) && brain != null) {
            return brain.getRound();
        }
        return -1;
    }
    
    @Override
    public void setDomainSize(int domainSize) {
        this.domainSize = domainSize;
        // Brain handles domain via constructor, this is for framework compatibility
    }
    
    // ========== INodeTransport implementation ==========
    
    @Override
    public void sendMessage(Message msg, int targetNodeId) {
        // Find target node by ID and send
        for (Edge edge : this.outgoingConnections) {
            if (edge.endNode.ID == targetNodeId) {
                send(msg, edge.endNode);
                return;
            }
        }
        // If not found in direct connections, try to find in all nodes
        // This shouldn't happen in a properly wired network
    }
    
    @Override
    public int getNodeId() {
        return this.ID;
    }
    
    // ========== Sinalgo Node lifecycle methods ==========
    
    @Override
    public void init() {
        if (brain != null) {
            brain.init();
        }
    }
    
    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            int senderId = inbox.getSender().ID;
            if (brain != null) {
                brain.handleMessage(msg, senderId);
            }
        }
    }
    
    @Override
    public void preStep() {
        // Let brain process any pending work (deferred message sending)
        if (brain != null) {
            brain.tick();
        }
    }
    
    @Override
    public void postStep() {
        // Nothing needed
    }
    
    @Override
    public void neighborhoodChange() {
        // Not used - network is static
    }
    
    @Override
    public void checkRequirements() throws sinalgo.configuration.WrongConfigurationException {
        // No specific requirements
    }
    
    @Override
    public String toString() {
        if (brain == null) {
            return "PMaxSumNode[" + ID + "]";
        }
        String type = brain.isAgent() ? "Agent" : "Function";
        return "PMaxSumNode[" + ID + "](" + type + " " + brain.getIndex() + ")";
    }
    
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // Different colors for agents vs functions
        Color color;
        String text;
        
        if (brain == null) {
            color = Color.LIGHT_GRAY;
            text = "?";
        } else if (brain.isAgent()) {
            color = Color.ORANGE;
            text = String.valueOf(brain.getIndex());
        } else {
            color = Color.GRAY;
            // For functions, show both agent IDs
            int idx = brain.getIndex();
            // Decode from negative encoded form
            if (idx < 0) {
                idx = -idx;
                int a = idx / 1000;
                int b = idx % 1000;
                text = "f" + a + "," + b;
            } else {
                text = "f" + idx;
            }
        }
        
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 12, color);
    }
}
