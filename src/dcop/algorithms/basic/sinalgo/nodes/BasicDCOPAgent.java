package dcop.algorithms.basic.sinalgo.nodes;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Random;

import dcop.common.nodes.IDCOPAgent;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;

/**
 * A DCOP agent implementing the Basic (random) algorithm.
 * 
 * This implementation uses a simple random algorithm where each agent
 * randomly selects a value from its domain. This serves as a baseline
 * for comparing more sophisticated DCOP algorithms.
 */
public class BasicDCOPAgent extends Node implements IDCOPAgent {
    
    // Agent state
    private int selectedValue;
    private int domainSize;
    private long algorithmSeed;
    private boolean isActive;
    private Random random;
    
    /**
     * Default constructor.
     */
    public BasicDCOPAgent() {
        this.selectedValue = 0;
        this.domainSize = 5; // Default
        this.algorithmSeed = 0;
        this.isActive = false;
    }
    
    // IDCOPAgent interface implementation
    
    @Override
    public void setDomainSize(int domainSize) {
        this.domainSize = domainSize;
    }
    
    public void setAlgorithmSeed(long seed) {
        this.algorithmSeed = seed;
    }
    
    @Override
    public void startAlgorithm() {
        this.isActive = true;
        
        // Simple random algorithm: select a random value
        this.selectedValue = random.nextInt(domainSize);
    }
    
    @Override
    public void stopAlgorithm() {
        this.isActive = false;
    }
    
    @Override
    public int getSelectedValue() {
        return selectedValue;
    }
    
    @Override
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public int getID() {
        return this.ID; // Delegate to Sinalgo's Node.ID
    }
    
    @Override
    public int getProperty(String property) {
        // Basic algorithm has no round concept
        return -1;  // Unsupported property
    }
    
    // Sinalgo Node abstract methods
    
    /**
     * Initializes the agent.
     * Called automatically after node creation.
     */
    @Override
    public void init() {
        // Initialize random generator with seed based on agent ID
        // This ensures different agents make different random choices
        this.random = new Random(algorithmSeed + this.ID);
        
        // Initially select a random value
        this.selectedValue = random.nextInt(domainSize);
        this.isActive = false;
    }
    
    /**
     * Called before each simulation step.
     */
    @Override
    public void preStep() {
        // Nothing to do before each step for simple random algorithm
    }
    
    /**
     * Called after each simulation step.
     */
    @Override
    public void postStep() {
        // Nothing to do after each step for simple random algorithm
    }
    
    /**
     * Called when the neighborhood (connections) of this node changes.
     */
    @Override
    public void neighborhoodChange() {
        // Could update neighbor list here if needed for coordinated algorithms
    }
    
    /**
     * Checks if the configuration requirements for this node are met.
     */
    @Override
    public void checkRequirements() throws sinalgo.configuration.WrongConfigurationException {
        // No specific requirements to check
    }
    
    /**
     * Handles incoming messages from neighbors.
     * Basic algorithm doesn't use messaging - ignores all messages.
     */
    @Override
    public void handleMessages(Inbox inbox) {
        // Basic algorithm doesn't communicate - ignore all messages
    }
    
    /**
     * Draws the node on the GUI.
     * Displays the agent ID minimally.
     */
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // Draw the node circle
        String text = String.valueOf(this.ID);
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 12, Color.YELLOW);
    }
    
    /**
     * Returns a short description of this node for tooltips.
     */
    @Override
    public String toString() {
        return "BasicDCOPAgent " + ID + " (value=" + selectedValue + ")";
    }
}
