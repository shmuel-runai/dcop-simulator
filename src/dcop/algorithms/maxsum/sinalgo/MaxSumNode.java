package dcop.algorithms.maxsum.sinalgo;

import java.awt.Color;
import java.awt.Graphics;

import dcop.algorithms.maxsum.IMaxSumBrain;
import dcop.algorithms.maxsum.INodeTransport;
import dcop.common.nodes.IDCOPAgent;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;

/**
 * Generic Sinalgo node for vanilla Max-Sum algorithm.
 * 
 * Works with both AgentBrain and FunctionBrain through the IMaxSumBrain interface.
 * This is the ONLY Sinalgo-specific class for nodes - all algorithm logic
 * lives in the brain implementations.
 */
public class MaxSumNode extends Node implements IDCOPAgent, INodeTransport {
    
    // The brain that contains all algorithm logic
    private IMaxSumBrain brain;
    
    // DCOP agent index (1..N for agents, negative for functions)
    private int dcopIndex;
    
    // Domain size (for agents only)
    private int domainSize;
    
    /**
     * Default constructor required by Sinalgo.
     */
    public MaxSumNode() {
        this.dcopIndex = -1;
        this.domainSize = 5;
    }
    
    // ========== Brain injection ==========
    
    public void setBrain(IMaxSumBrain brain) {
        this.brain = brain;
    }
    
    public IMaxSumBrain getBrain() {
        return brain;
    }
    
    public void setDcopIndex(int index) {
        this.dcopIndex = index;
    }
    
    // ========== IDCOPAgent implementation ==========
    
    @Override
    public int getID() {
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
        // Nothing to do
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
    }
    
    // ========== INodeTransport implementation ==========
    
    @Override
    public void sendMessage(Message msg, int targetNodeId) {
        for (Edge edge : this.outgoingConnections) {
            if (edge.endNode.ID == targetNodeId) {
                send(msg, edge.endNode);
                return;
            }
        }
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
            return "MaxSumNode[" + ID + "]";
        }
        String type = brain.isAgent() ? "Agent" : "Function";
        return "MaxSumNode[" + ID + "](" + type + " " + brain.getIndex() + ")";
    }
    
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        Color color;
        String text;
        
        if (brain == null) {
            color = Color.LIGHT_GRAY;
            text = "?";
        } else if (brain.isAgent()) {
            color = Color.CYAN;  // Cyan for vanilla Max-Sum agents (vs Orange for P-MAXSUM)
            text = String.valueOf(brain.getIndex());
        } else {
            color = Color.DARK_GRAY;
            int idx = brain.getIndex();
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
