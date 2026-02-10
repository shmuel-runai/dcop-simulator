package dcop.algorithms.pmaxsum;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import crypto.utils.Paillier;
import crypto.utils.PaillierMgr;
import dcop.algorithms.pmaxsum.messages.*;
import sinalgo.nodes.messages.Message;
import sinalgo.runtime.Global;

/**
 * Agent brain for P-MAXSUM algorithm.
 * 
 * Implements the agent-side logic for privacy-preserving Max-Sum using 
 * Paillier homomorphic encryption.
 * 
 * Ported from reference implementation with bug fixes.
 */
public class AgentBrain implements IMaxSumBrain {
    
    // Debug flag
    private static final boolean DEBUG = false;
    
    // Paillier key prefixes
    private static final String E_PAILLIER_KEY = "E";
    private static final String F_PAILLIER_KEY = "F";
    
    // Identity
    private final int agentIndex;
    private final int domainSize;
    
    // Transport (injected after construction)
    private INodeTransport transport;
    
    // Neighbors: otherAgentId → functionNodeId (Sinalgo ID)
    private final Map<Integer, Integer> functionNeighbors;
    
    // Crypto
    private final PaillierMgr paillierMgr;
    private final BigInteger prime;
    
    // Round state
    private int currentRound;
    private final int lastRound;
    private final Map<String, BigInteger> variables;
    
    // Sync counters for waiting on messages
    private final Map<String, Integer> syncCounters;
    
    // Result
    private int selectedValue;
    private boolean done;
    private boolean running;
    
    // Random generator
    private Random random;
    
    /**
     * Create an AgentBrain.
     * 
     * @param agentIndex DCOP agent ID (1-based)
     * @param domainSize Number of domain values
     * @param lastRound Number of rounds before termination
     * @param paillierMgr Shared Paillier key manager
     * @param prime Prime modulus for field arithmetic
     */
    public AgentBrain(int agentIndex, int domainSize, int lastRound, 
                      PaillierMgr paillierMgr, BigInteger prime) {
        this.agentIndex = agentIndex;
        this.domainSize = domainSize;
        this.lastRound = lastRound;
        this.paillierMgr = paillierMgr;
        this.prime = prime;
        
        this.functionNeighbors = new HashMap<>();
        this.variables = new HashMap<>();
        this.syncCounters = new HashMap<>();
        this.selectedValue = -1;
        this.done = false;
        this.running = false;
        
        // Create Paillier keys for this agent
        paillierMgr.put(ePaillierKey(agentIndex), new Paillier());
        paillierMgr.put(fPaillierKey(agentIndex), new Paillier());
    }
    
    // ========== Paillier key helpers ==========
    
    private String ePaillierKey(int index) {
        return E_PAILLIER_KEY + "-" + index;
    }
    
    private String fPaillierKey(int index) {
        return F_PAILLIER_KEY + "-" + index;
    }
    
    // ========== Variable key helpers ==========
    
    private String rKey(int round, int affinity, int source, int target, int index) {
        return String.format("R^%d,%d_%d-->%d(%d)", round, affinity, source, target, index);
    }
    
    private String qKey(int round, int affinity, int source, int target, int index) {
        return String.format("Q^%d,%d_%d-->%d(%d)", round, affinity, source, target, index);
    }
    
    private String initRsWaiterKey(int round) {
        return "Init-R-" + round;
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
        variables.clear();
        syncCounters.clear();
        random = new Random();
        currentRound = 0;
        selectedValue = random.nextInt(domainSize); // Default value
    }
    
    @Override
    public void handleMessage(Message msg, int senderNodeId) {
        if (msg instanceof InjectRsMessage) {
            handleInjectRs((InjectRsMessage) msg);
        } else if (msg instanceof ProcessWsRequestMessage) {
            handleProcessWsRequest((ProcessWsRequestMessage) msg, senderNodeId);
        } else if (msg instanceof MinIndexResponseMessage) {
            handleMinIndexResponse((MinIndexResponseMessage) msg);
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
        // Agent brains respond to FunctionBrain, but if there are no function neighbors
        // (isolated agent), we need to self-start and finish immediately
        if (running && functionNeighbors.isEmpty() && currentRound == 0) {
            debug("No function neighbors - self-starting isolated agent");
            kickStartRound();
        }
    }
    
    // ========== Message Handlers ==========
    
    private void handleInjectRs(InjectRsMessage msg) {
        debug("Handling InjectRsMessage: " + msg);
        
        for (int x = 0; x < domainSize; x++) {
            // Store local Rs (encrypted with F_target)
            variables.put(rKey(msg.round, msg.source, msg.source, msg.target, x), msg.local[x]);
            
            if (msg.withRemote && msg.remote != null) {
                // Store remote Rs (plaintext)
                variables.put(rKey(msg.round, msg.target, msg.source, msg.target, x), msg.remote[x]);
            }
        }
        
        // Wait until all function neighbors have sent their Rs
        boolean allReceived = tickSync(initRsWaiterKey(msg.round), functionNeighbors.size());
        if (!allReceived) {
            return;
        }
        
        kickStartRound();
    }
    
    private void kickStartRound() {
        currentRound++;
        debug("Kick start round: " + currentRound);
        
        if (currentRound == lastRound) {
            kickStartLastRound();
            return;
        }
        
        // If no function neighbors (isolated agent), proceed to next round immediately
        if (functionNeighbors.isEmpty()) {
            debug("No function neighbors - proceeding to next round immediately");
            kickStartRound();
            return;
        }
        
        // Start protocol 1 with all function neighbors
        for (Integer otherId : functionNeighbors.keySet()) {
            calcQs(otherId);
        }
    }
    
    private void calcQs(int otherId) {
        debug("Calculating Qs for otherId: " + otherId);
        
        Integer functionNodeId = functionNeighbors.get(otherId);
        if (functionNodeId == null) {
            debug("ERROR: Function node not found for otherId: " + otherId);
            return;
        }
        
        // Initialize Q arrays
        BigInteger[] localQs = new BigInteger[domainSize];
        BigInteger[] remoteQs = new BigInteger[domainSize];
        for (int x = 0; x < domainSize; x++) {
            localQs[x] = BigInteger.ZERO;
            // Initialize remoteQs with E(0) - encryption of zero.
            // Using BigInteger.ONE is WRONG because 1 is not a valid Paillier ciphertext.
            // When there are no other function neighbors, remoteQs stays at this value
            // and must decrypt to 0.
            remoteQs[x] = paillierMgr.Encryption(fPaillierKey(agentIndex), BigInteger.ZERO);
        }
        
        // Sum R's from all other function neighbors (excluding otherId)
        for (Integer fId : functionNeighbors.keySet()) {
            if (fId.equals(otherId)) {
                continue;  // Skip the target
            }
            
            for (int x = 0; x < domainSize; x++) {
                // Local Qs: sum of my R's (plaintext)
                String myRKey = rKey(currentRound - 1, agentIndex, fId, agentIndex, x);
                BigInteger myR = variables.get(myRKey);
                if (myR == null) {
                    debug("ERROR: Unable to find my R key: " + myRKey);
                    continue;
                }
                localQs[x] = localQs[x].add(myR).mod(prime);
                
                // Remote Qs: product of other's encrypted R's
                String otherRKey = rKey(currentRound - 1, fId, fId, agentIndex, x);
                BigInteger otherR = variables.get(otherRKey);
                if (otherR == null) {
                    debug("ERROR: Unable to find other R key: " + otherRKey);
                    continue;
                }
                // otherR is encrypted with F_agentIndex, multiply (homomorphic add)
                // MUST use F_agentIndex's nsquare for correct Paillier homomorphic addition
                BigInteger nsquare = paillierMgr.get(fPaillierKey(agentIndex)).nsquare;
                remoteQs[x] = remoteQs[x].multiply(otherR).mod(nsquare);
            }
        }
        
        // Store local Qs and encrypt for sending
        BigInteger[] encLocalQs = new BigInteger[domainSize];
        for (int x = 0; x < domainSize; x++) {
            variables.put(qKey(currentRound, agentIndex, agentIndex, otherId, x), localQs[x]);
            encLocalQs[x] = paillierMgr.Encryption(ePaillierKey(agentIndex), localQs[x]);
        }
        
        // Send Q message to function node
        InjectQsMessage msg = new InjectQsMessage(currentRound, agentIndex, otherId, encLocalQs, remoteQs);
        debug("Sending InjectQsMessage to node " + functionNodeId);
        transport.sendMessage(msg, functionNodeId);
    }
    
    private void handleProcessWsRequest(ProcessWsRequestMessage msg, int senderNodeId) {
        debug("Handling ProcessWsRequestMessage: " + msg);
        
        BigInteger[] results = new BigInteger[msg.Ws.length];
        
        for (int i = 0; i < msg.Ws.length; i++) {
            // Find the minimum decrypted value in this row
            // CRITICAL BUG FIX: Ws were encrypted with ePaillierKey(msg.source) in kickStartProtocol2,
            // so we must decrypt with the same key, NOT ePaillierKey(agentIndex).
            // The reference implementation had this same bug.
            BigInteger min = paillierMgr.Decryption(ePaillierKey(msg.source), msg.Ws[i][0]);
            for (int j = 1; j < msg.Ws[i].length; j++) {
                BigInteger value = paillierMgr.Decryption(ePaillierKey(msg.source), msg.Ws[i][j]);
                min = min.min(value);
            }
            
            // Generate random share for my R
            BigInteger r = BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)).mod(prime);
            
            // Store my R for the next round
            String myRKey = rKey(msg.round, agentIndex, msg.source, agentIndex, i);
            debug("Storing myR: " + myRKey + " = " + r);
            variables.put(myRKey, r);
            
            // Return min - r (shifted by our random value)
            results[i] = min.subtract(r).mod(prime);
        }
        
        // Send response back
        ProcessWsResponseMessage response = new ProcessWsResponseMessage(
            msg.round, msg.source, msg.source, msg.target, results, msg.shifter);
        debug("Sending ProcessWsResponseMessage");
        transport.sendMessage(response, senderNodeId);
    }
    
    private void kickStartLastRound() {
        debug("Last round: " + currentRound);
        
        // If no function neighbors (isolated agent), just pick default value
        if (functionNeighbors.isEmpty()) {
            debug("No function neighbors - selecting default value 0");
            selectedValue = 0;
            running = false;
            done = true;
            return;
        }
        
        // Generate shifter to hide actual values
        // CRITICAL BUG FIX: encShifter must be encrypted with F_agentIndex (not E_agentIndex)
        // because Ms contains F_agentIndex ciphertexts, and the function will decrypt with F_agentIndex.
        // Using E_agentIndex here was a bug from the reference implementation.
        BigInteger shifter = BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)).mod(prime);
        BigInteger encShifter = paillierMgr.Encryption(fPaillierKey(agentIndex), shifter);
        
        // Initialize M array with E(0) - encryption of zero
        // Using BigInteger.ONE is WRONG because 1 is not a valid Paillier ciphertext.
        BigInteger[] Ms = new BigInteger[domainSize];
        for (int x = 0; x < domainSize; x++) {
            Ms[x] = paillierMgr.Encryption(fPaillierKey(agentIndex), BigInteger.ZERO);
        }
        
        // Pick any function node to send the request to
        int functionId = -1;
        int functionNodeId = -1;
        
        // Sum all R's from all function neighbors
        for (Integer fId : functionNeighbors.keySet()) {
            functionId = fId;
            functionNodeId = functionNeighbors.get(fId);
            
            for (int x = 0; x < domainSize; x++) {
                String myRKey = rKey(currentRound - 1, agentIndex, fId, agentIndex, x);
                BigInteger myR = variables.get(myRKey);
                if (myR == null) {
                    debug("ERROR: Unable to find local R key: " + myRKey);
                    continue;
                }
                
                String otherRKey = rKey(currentRound - 1, fId, fId, agentIndex, x);
                BigInteger otherR = variables.get(otherRKey);
                if (otherR == null) {
                    debug("ERROR: Unable to find other R key: " + otherRKey);
                    continue;
                }
                
                // Encrypt my local R with F_agentIndex
                BigInteger encLocalR = paillierMgr.Encryption(fPaillierKey(agentIndex), myR);
                
                // === BUG FROM REFERENCE PORT ===
                // Original buggy code:
                // Ms[x] = Ms[x].multiply(encLocalR).mod(p).multiply(otherR).mod(p);
                // Problem: This mixes ciphertext and plaintext multiplication incorrectly.
                // - encLocalR is a ciphertext (encrypted with F_agentIndex)
                // - otherR is also a ciphertext (encrypted with F_agentIndex)
                // - We need to multiply ciphertexts to homomorphically add plaintexts
                // === END BUG ===
                
                // FIX: Both are ciphertexts encrypted with F_agentIndex, multiply them
                // MUST use F_agentIndex's nsquare for correct Paillier homomorphic addition
                BigInteger nsquare = paillierMgr.get(fPaillierKey(agentIndex)).nsquare;
                Ms[x] = Ms[x].multiply(encLocalR).mod(nsquare).multiply(otherR).mod(nsquare);
            }
        }
        
        // Apply shifter (multiply all Ms by encrypted shifter)
        // Use F_agentIndex's nsquare for correct Paillier operation
        BigInteger nsquareForShifter = paillierMgr.get(fPaillierKey(agentIndex)).nsquare;
        for (int x = 0; x < Ms.length; x++) {
            Ms[x] = Ms[x].multiply(encShifter).mod(nsquareForShifter);
        }
        
        // TODO: Permute Ms for additional privacy
        
        // Send to any function neighbor to find min
        if (functionNodeId != -1) {
            MinIndexRequestMessage msg = new MinIndexRequestMessage(agentIndex, Ms);
            debug("Sending MinIndexRequestMessage");
            transport.sendMessage(msg, functionNodeId);
        }
    }
    
    private void handleMinIndexResponse(MinIndexResponseMessage msg) {
        selectedValue = msg.index;
        debug("Selected value: " + selectedValue);
        running = false;
        done = true;
    }
    
    // ========== Sync helper ==========
    
    private boolean tickSync(String key, int target) {
        int count = syncCounters.getOrDefault(key, 0) + 1;
        syncCounters.put(key, count);
        return count >= target;
    }
    
    // ========== Debug helper ==========
    
    private void debug(String msg) {
        if (DEBUG) {
            Global.log.logln(true, "AgentBrain[" + agentIndex + "]: " + msg);
        }
    }
}
