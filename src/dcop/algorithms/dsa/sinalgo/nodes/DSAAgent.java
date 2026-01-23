package dcop.algorithms.dsa.sinalgo.nodes;

import java.awt.Color;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import dcop.common.DCOPProblem;
import dcop.common.nodes.IDCOPAgent;
import dcop.algorithms.dsa.sinalgo.messages.ValueMessage;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;

/**
 * A DCOP agent implementing the DSA (Distributed Stochastic Algorithm).
 * 
 * DSA is a local search algorithm where agents iteratively try to improve
 * their value assignment based on neighbor values, with probabilistic decisions.
 */
public class DSAAgent extends Node implements IDCOPAgent {
    
    // Agent state
    private int selectedValue;
    private int domainSize;
    private long algorithmSeed;
    private boolean isActive;
    private Random random;
    
    // DSA-specific fields
    private double stochastic;  // Probability of changing to better value (0.0 to 1.0)
    private DCOPProblem problem;  // Reference to problem for cost calculation
    private Map<Integer, Integer> neighborValues;  // Cache of neighbor values (agentID -> value)
    private boolean firstStep;  // Track if this is the first step
    
    // Round limiting
    private int maxRounds = -1;  // -1 = unlimited, 0 = no rounds, >0 = limit
    private int roundCount = 0;  // Number of rounds executed
    
    /**
     * Default constructor.
     */
    public DSAAgent() {
        this.selectedValue = 0;
        this.domainSize = 5; // Default
        this.algorithmSeed = 0;
        this.isActive = false;
        this.stochastic = 0.8;  // Default stochastic value
        this.neighborValues = new HashMap<>();
        this.firstStep = true;
        this.maxRounds = -1;  // Unlimited by default
        this.roundCount = 0;
    }
    
    /**
     * Sets the stochastic parameter for DSA.
     * This is the probability of changing when a better value is found.
     * 
     * @param stochastic Probability (0.0 to 1.0)
     */
    public void setStochastic(double stochastic) {
        this.stochastic = stochastic;
    }
    
    /**
     * Sets the problem reference for cost calculation.
     * 
     * @param problem The DCOP problem
     */
    public void setProblem(DCOPProblem problem) {
        this.problem = problem;
    }
    
    /**
     * Sets the maximum number of rounds this agent will execute.
     * 
     * @param maxRounds -1 for unlimited, 0 for no rounds, >0 for limit
     */
    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
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
        this.roundCount = 0;
        this.firstStep = true;
        this.neighborValues.clear();
        
        // If maxRounds == 0, don't run any rounds
        if (maxRounds == 0) {
            this.isActive = false;
        } else {
            this.isActive = true;
        }
    }
    
    @Override
    public void stopAlgorithm() {
        this.isActive = false;
        
        // CLEANUP: Clear resources to free memory between iterations
        this.neighborValues.clear();
        this.problem = null;
        // Note: roundCount is NOT reset here - it's needed for reporting
        // It will be reset in startAlgorithm() for next iteration
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
        return this.ID;
    }
    
    @Override
    public int getProperty(String property) {
        if ("rounds".equals(property)) {
            return roundCount;
        }
        return -1;  // Unsupported property
    }
    
    // Sinalgo Node abstract methods
    
    @Override
    public void init() {
        // Initialize random generator with seed based on agent ID
        this.random = new Random(algorithmSeed + this.ID);
        
        // Initially select a random value
        this.selectedValue = random.nextInt(domainSize);
        this.isActive = false;
        this.neighborValues = new HashMap<>();
        this.firstStep = true;
    }
    
    /**
     * DSA algorithm logic - executed before each simulation step.
     */
    @Override
    public void preStep() {
        if (!isActive) {
            return;  // Not running
        }
        
        // Check if we've reached max rounds
        if (maxRounds >= 0 && roundCount >= maxRounds) {
            this.isActive = false;
            return;
        }
        
        // Step 1: Broadcast current value to all neighbors
        broadcastValue();
    }
    
    /**
     * Called after each simulation step.
     * This is where we execute the DSA decision logic after receiving neighbor messages.
     */
    @Override
    public void postStep() {
        if (!isActive) {
            return;
        }
        
        // Skip DSA logic on first step (need to receive neighbor values first)
        if (firstStep) {
            firstStep = false;
            return;
        }
        
        // Execute DSA decision logic
        executeDSALogic();
        
        // Increment round count after each DSA decision
        roundCount++;
    }
    
    /**
     * Broadcasts current value to all neighbors.
     */
    private void broadcastValue() {
        ValueMessage msg = new ValueMessage(this.ID, this.selectedValue);
        
        // Send to all outgoing connections
        for (Edge edge : this.outgoingConnections) {
            send(msg, edge.endNode);
        }
    }
    
    /**
     * Executes the core DSA algorithm logic.
     * Evaluates all alternative values and probabilistically switches to best one.
     */
    private void executeDSALogic() {
        if (problem == null) {
            // Cannot execute DSA without problem reference
            return;
        }
        
        // Step 2: Calculate current cost
        int currentCost = calculateCurrentCost();
        
        // Step 3: Find best alternative value
        int bestValue = selectedValue;
        int bestCost = currentCost;
        
        for (int candidateValue = 0; candidateValue < domainSize; candidateValue++) {
            if (candidateValue == selectedValue) {
                continue;  // Skip current value
            }
            
            // Calculate cost if we switch to this value
            int candidateCost = calculateCostForValue(candidateValue);
            
            if (candidateCost < bestCost) {
                bestValue = candidateValue;
                bestCost = candidateCost;
            }
        }
        
        // Step 4: Probabilistic decision
        if (bestCost < currentCost) {
            // Found an improvement
            double r = random.nextDouble();
            if (r < stochastic) {
                // Switch to better value
                selectedValue = bestValue;
            }
            // else: stay with current value
        }
        // else: no improvement found, stay with current value
    }
    
    /**
     * Calculates the current cost based on current value and neighbor values.
     * 
     * @return Total cost with all neighbors
     */
    private int calculateCurrentCost() {
        return calculateCostForValue(selectedValue);
    }
    
    /**
     * Calculates the cost if this agent were to select the given value.
     * 
     * @param value The value to evaluate
     * @return Total cost with all neighbors if this value were selected
     */
    private int calculateCostForValue(int value) {
        int totalCost = 0;
        
        for (Map.Entry<Integer, Integer> entry : neighborValues.entrySet()) {
            int neighborID = entry.getKey();
            int neighborValue = entry.getValue();
            
            // Get cost from problem
            int cost = problem.getCost(this.ID, neighborID, value, neighborValue);
            totalCost += cost;
        }
        
        return totalCost;
    }
    
    @Override
    public void neighborhoodChange() {
        // Could update neighbor list here if needed
    }
    
    @Override
    public void checkRequirements() throws sinalgo.configuration.WrongConfigurationException {
        // No specific requirements
    }
    
    /**
     * Handles incoming messages from neighbors.
     */
    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            if (msg instanceof ValueMessage) {
                ValueMessage vm = (ValueMessage) msg;
                // Store neighbor's value
                neighborValues.put(vm.senderID, vm.selectedValue);
            }
        }
    }
    
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // Draw the node circle
        String text = String.valueOf(this.ID);
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 12, Color.GREEN);
    }
    
    @Override
    public String toString() {
        return "DSAAgent " + ID + " (value=" + selectedValue + ", stochastic=" + stochastic + ")";
    }
}





