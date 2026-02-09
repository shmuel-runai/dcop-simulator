package dcop.algorithms.maxsum;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import dcop.algorithms.maxsum.messages.*;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.Global;

/**
 * Agent brain for vanilla (non-private) Max-Sum algorithm.
 * 
 * Implements the agent-side logic for Max-Sum without any encryption.
 * Much simpler than the privacy-preserving version.
 * 
 * Algorithm:
 * 1. Initialize R values to 0
 * 2. Each round: compute Q (sum of R's excluding target), send to function nodes
 * 3. Receive R values back from function nodes
 * 4. After last round: select value with minimum sum of R's
 */
public class AgentBrain implements IMaxSumBrain {
    
    // Debug flag
    private static final boolean DEBUG = false;
    
    // Identity
    private final int agentIndex;
    private final int domainSize;
    
    // Transport (injected after construction)
    private INodeTransport transport;
    
    // Neighbors: otherAgentId → functionNodeId (Sinalgo ID)
    private final Map<Integer, Integer> functionNeighbors;
    
    // Round state
    private int currentRound;
    private final int lastRound;
    
    // R values storage: key "R^round_otherId(x)" → value
    private final Map<String, BigInteger> rValues;
    
    // Sync counter for waiting on R messages
    private final Map<Integer, Integer> rSyncCounters;
    
    // Result
    private int selectedValue;
    private boolean done;
    private boolean running;
    
    // Deferred start flag (message sending must wait for simulation cycle)
    private boolean pendingStart;
    
    /**
     * Create an AgentBrain for vanilla Max-Sum.
     * 
     * @param agentIndex DCOP agent ID (1-based)
     * @param domainSize Number of domain values
     * @param lastRound Number of rounds before termination
     */
    public AgentBrain(int agentIndex, int domainSize, int lastRound) {
        this.agentIndex = agentIndex;
        this.domainSize = domainSize;
        this.lastRound = lastRound;
        
        this.functionNeighbors = new HashMap<>();
        this.rValues = new HashMap<>();
        this.rSyncCounters = new HashMap<>();
        this.selectedValue = -1;
        this.done = false;
        this.running = false;
        this.pendingStart = false;
    }
    
    // ========== Key helpers ==========
    
    private String rKey(int round, int otherId, int index) {
        return String.format("R^%d_%d-->%d(%d)", round, otherId, agentIndex, index);
    }
    
    // ========== Injection setters ==========
    
    public void setTransport(INodeTransport transport) {
        this.transport = transport;
    }
    
    /**
     * Add a function neighbor.
     * 
     * @param otherAgentId The other agent connected through this function
     * @param functionNodeId The Sinalgo node ID of the function node
     */
    public void addFunctionNeighbor(int otherAgentId, int functionNodeId) {
        functionNeighbors.put(otherAgentId, functionNodeId);
    }
    
    // ========== IMaxSumBrain implementation ==========
    
    @Override
    public void init() {
        // Nothing specific needed
    }
    
    @Override
    public void start() {
        running = true;
        done = false;
        rValues.clear();
        rSyncCounters.clear();
        currentRound = 0;
        selectedValue = -1;
        
        // Initialize R values to 0 for round 0
        for (Integer otherId : functionNeighbors.keySet()) {
            for (int x = 0; x < domainSize; x++) {
                rValues.put(rKey(0, otherId, x), BigInteger.ZERO);
            }
        }
        
        // Mark for deferred start - message sending happens in tick()
        pendingStart = true;
    }
    
    @Override
    public void handleMessage(Message msg, int senderNodeId) {
        if (msg instanceof UpdateRsMessage) {
            handleUpdateRs((UpdateRsMessage) msg);
        }
    }
    
    @Override
    public boolean isDone() {
        return done;
    }
    
    @Override
    public int getAssignment() {
        return selectedValue;
    }
    
    @Override
    public int getRound() {
        return currentRound;
    }
    
    @Override
    public void logState() {
        debug("State of Agent " + agentIndex);
        debug("  Domain size: " + domainSize);
        debug("  Round: " + currentRound + "/" + lastRound);
        debug("  Function Neighbors:");
        for (Integer otherId : functionNeighbors.keySet()) {
            debug("    with Agent " + otherId + " via Node " + functionNeighbors.get(otherId));
        }
    }
    
    @Override
    public int getIndex() {
        return agentIndex;
    }
    
    @Override
    public boolean isAgent() {
        return true;
    }
    
    @Override
    public void tick() {
        if (pendingStart) {
            pendingStart = false;
            kickStartRound();
        }
    }
    
    // ========== Algorithm Logic ==========
    
    private void kickStartRound() {
        currentRound++;
        debug("Kick start round: " + currentRound);
        
        if (currentRound == lastRound) {
            finishRun();
            return;
        }
        
        // If no function neighbors (isolated agent), proceed to next round immediately
        if (functionNeighbors.isEmpty()) {
            debug("No function neighbors - proceeding to next round immediately");
            kickStartRound();
            return;
        }
        
        // Send Q message to each function neighbor
        for (Integer targetId : functionNeighbors.keySet()) {
            sendQsToFunction(targetId);
        }
    }
    
    private void sendQsToFunction(int targetId) {
        // Compute Q = sum of R's from all function neighbors EXCEPT targetId
        BigInteger[] Qs = new BigInteger[domainSize];
        for (int x = 0; x < domainSize; x++) {
            Qs[x] = BigInteger.ZERO;
        }
        
        for (Integer otherId : functionNeighbors.keySet()) {
            if (otherId.equals(targetId)) {
                continue;  // Skip the target
            }
            
            for (int x = 0; x < domainSize; x++) {
                String key = rKey(currentRound - 1, otherId, x);
                BigInteger r = rValues.get(key);
                if (r == null) {
                    debug("ERROR: Unable to find R key: " + key);
                    r = BigInteger.ZERO;
                }
                Qs[x] = Qs[x].add(r);
            }
        }
        
        // Send to function node
        Integer functionNodeId = functionNeighbors.get(targetId);
        UpdateQsMessage msg = new UpdateQsMessage(targetId, currentRound, Qs);
        debug("Sending Qs " + msg.qString() + " to node " + functionNodeId);
        transport.sendMessage(msg, functionNodeId);
    }
    
    private void handleUpdateRs(UpdateRsMessage msg) {
        debug("Handling UpdateRsMessage: " + msg);
        
        // Store R values
        for (int x = 0; x < msg.r.length; x++) {
            rValues.put(rKey(msg.round, msg.otherId, x), msg.r[x]);
        }
        
        // Wait for all R messages from all function neighbors
        int count = rSyncCounters.getOrDefault(msg.round, 0) + 1;
        rSyncCounters.put(msg.round, count);
        
        if (count < functionNeighbors.size()) {
            debug("Still waiting for R messages (have " + count + ", need " + functionNeighbors.size() + ")");
            return;
        }
        
        // All R's received, proceed to next round
        debug("All R's received, proceeding to next round");
        kickStartRound();
    }
    
    private void finishRun() {
        debug("Finishing run");
        
        // Compute M = sum of all R's for each domain value
        BigInteger[] Ms = new BigInteger[domainSize];
        for (int x = 0; x < domainSize; x++) {
            Ms[x] = BigInteger.ZERO;
        }
        
        for (Integer otherId : functionNeighbors.keySet()) {
            for (int x = 0; x < domainSize; x++) {
                String key = rKey(currentRound - 1, otherId, x);
                BigInteger r = rValues.get(key);
                if (r == null) {
                    debug("ERROR: Unable to find R key: " + key);
                    r = BigInteger.ZERO;
                }
                Ms[x] = Ms[x].add(r);
            }
        }
        
        // Find index with minimum M
        BigInteger min = Ms[0];
        selectedValue = 0;
        for (int x = 1; x < domainSize; x++) {
            if (Ms[x].compareTo(min) < 0) {
                min = Ms[x];
                selectedValue = x;
            }
        }
        
        debug("Selected value: " + selectedValue + " with M=" + min);
        running = false;
        done = true;
    }
    
    // ========== Debug helper ==========
    
    private void debug(String msg) {
        if (DEBUG) {
            Global.log.logln(true, "AgentBrain[" + agentIndex + "]: " + msg);
        }
    }
}
