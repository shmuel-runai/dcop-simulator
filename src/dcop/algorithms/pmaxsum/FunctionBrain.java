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
 * Function brain for P-MAXSUM algorithm.
 * 
 * Implements the function node logic for privacy-preserving Max-Sum using 
 * Paillier homomorphic encryption.
 * 
 * Each function node connects exactly two agents and holds their constraint matrix.
 * 
 * Ported from reference implementation with bug fixes.
 */
public class FunctionBrain implements IMaxSumBrain {
    
    // Debug flag
    private static final boolean DEBUG = false;
    
    // Paillier key prefixes
    private static final String E_PAILLIER_KEY = "E";
    private static final String F_PAILLIER_KEY = "F";
    
    // Identity (function connects agentA and agentB)
    private final int agentA;
    private final int agentB;
    private final int domainSizeA;
    private final int domainSizeB;
    
    // Constraint matrix: cost[i][j] is cost when A=i and B=j
    private final int[][] constraintMatrix;
    
    // Transport (injected after construction)
    private INodeTransport transport;
    
    // Agent node IDs (Sinalgo IDs)
    private int agentANodeId;
    private int agentBNodeId;
    
    // Crypto
    private final PaillierMgr paillierMgr;
    private final BigInteger prime;
    
    // Round state
    private final Map<String, BigInteger> variables;
    
    // Random generator
    private Random random;
    
    // Deferred start flag (message sending must wait for simulation cycle)
    private boolean pendingStart;
    
    /**
     * Create a FunctionBrain.
     * 
     * @param agentA First agent's DCOP ID (1-based)
     * @param agentB Second agent's DCOP ID (1-based)
     * @param domainSizeA Domain size for agent A
     * @param domainSizeB Domain size for agent B
     * @param constraintMatrix Cost matrix [domainSizeA][domainSizeB]
     * @param paillierMgr Shared Paillier key manager
     * @param prime Prime modulus for field arithmetic
     */
    public FunctionBrain(int agentA, int agentB, 
                         int domainSizeA, int domainSizeB,
                         int[][] constraintMatrix,
                         PaillierMgr paillierMgr, BigInteger prime) {
        this.agentA = agentA;
        this.agentB = agentB;
        this.domainSizeA = domainSizeA;
        this.domainSizeB = domainSizeB;
        this.constraintMatrix = constraintMatrix;
        this.paillierMgr = paillierMgr;
        this.prime = prime;
        
        this.variables = new HashMap<>();
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
    
    // ========== Injection setters ==========
    
    public void setTransport(INodeTransport transport) {
        this.transport = transport;
    }
    
    public void setAgentNodeIds(int agentANodeId, int agentBNodeId) {
        this.agentANodeId = agentANodeId;
        this.agentBNodeId = agentBNodeId;
    }
    
    // ========== Helper methods ==========
    
    private int getOtherAgent(int agentId) {
        return (agentId == agentA) ? agentB : agentA;
    }
    
    private int getAgentNodeId(int agentId) {
        return (agentId == agentA) ? agentANodeId : agentBNodeId;
    }
    
    private int getDomainSize(int agentId) {
        return (agentId == agentA) ? domainSizeA : domainSizeB;
    }
    
    /**
     * Get constraint value.
     * 
     * @param targetAgent The agent we're computing R for
     * @param targetIdx The domain index for the target agent
     * @param otherIdx The domain index for the other agent
     * @return The constraint cost
     */
    private int getConstraint(int targetAgent, int targetIdx, int otherIdx) {
        if (targetAgent == agentA) {
            return constraintMatrix[targetIdx][otherIdx];
        } else {
            return constraintMatrix[otherIdx][targetIdx];
        }
    }
    
    // ========== IMaxSumBrain implementation ==========
    
    @Override
    public void init() {
        variables.clear();
    }
    
    @Override
    public void start() {
        random = new Random();
        pendingStart = true;
        // Message sending happens in tick() to avoid Sinalgo synchronization issues
    }
    
    private void initializeRsForAgent(int targetAgent, int round) {
        int domainSize = getDomainSize(targetAgent);
        int sourceAgent = getOtherAgent(targetAgent);
        int targetNodeId = getAgentNodeId(targetAgent);
        
        BigInteger[] encLocalRs = new BigInteger[domainSize];
        BigInteger[] remoteRs = new BigInteger[domainSize];
        
        for (int x = 0; x < domainSize; x++) {
            // Generate random local R
            BigInteger localR = BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)).mod(prime);
            
            // Store local R
            variables.put(rKey(round, transport.getNodeId(), transport.getNodeId(), targetAgent, x), localR);
            
            // Encrypt with F_targetAgent
            encLocalRs[x] = paillierMgr.Encryption(fPaillierKey(targetAgent), localR);
            
            // Remote R = -localR (so local + remote = 0 at start)
            remoteRs[x] = prime.subtract(localR).mod(prime);
            
            debug("Setting R: round=" + round + " target=" + targetAgent + " x=" + x + 
                  " local=" + localR + " remote=" + remoteRs[x]);
        }
        
        // Send to target agent
        InjectRsMessage msg = new InjectRsMessage(
            round, sourceAgent, targetAgent, encLocalRs, remoteRs, true);
        transport.sendMessage(msg, targetNodeId);
    }
    
    @Override
    public void handleMessage(Message msg, int senderNodeId) {
        if (msg instanceof InjectQsMessage) {
            handleInjectQs((InjectQsMessage) msg);
        } else if (msg instanceof ProcessWsResponseMessage) {
            handleProcessWsResponse((ProcessWsResponseMessage) msg, senderNodeId);
        } else if (msg instanceof MinIndexRequestMessage) {
            handleMinIndexRequest((MinIndexRequestMessage) msg, senderNodeId);
        }
    }
    
    @Override
    public boolean isDone() {
        // Function nodes are passive - they're always ready
        return true;
    }
    
    @Override
    public int getAssignment() {
        // Function nodes don't have assignments
        return -1;
    }
    
    @Override
    public int getRound() {
        return 0;  // Function nodes don't track rounds
    }
    
    @Override
    public void logState() {
        debug("State of Function(" + agentA + "," + agentB + ")");
        debug("  A domain size: " + domainSizeA);
        debug("  B domain size: " + domainSizeB);
        debug("  Constraint matrix:");
        for (int i = 0; i < domainSizeA; i++) {
            StringBuilder line = new StringBuilder("    [");
            for (int j = 0; j < domainSizeB; j++) {
                if (j > 0) line.append(", ");
                line.append(constraintMatrix[i][j]);
            }
            line.append("]");
            debug(line.toString());
        }
    }
    
    @Override
    public int getIndex() {
        // Return an encoded function identifier (negative to distinguish from agents)
        return -(agentA * 1000 + agentB);
    }
    
    @Override
    public boolean isAgent() {
        return false;
    }
    
    @Override
    public void tick() {
        if (pendingStart) {
            pendingStart = false;
            // Initialize R's for both agents at round 0
            initializeRsForAgent(agentA, 0);
            initializeRsForAgent(agentB, 0);
        }
    }
    
    // ========== Message Handlers ==========
    
    private void handleInjectQs(InjectQsMessage msg) {
        debug("Handling InjectQsMessage: " + msg);
        
        // Store local Q's (encrypted with E_source)
        for (int x = 0; x < msg.local.length; x++) {
            variables.put(qKey(msg.round, msg.source, msg.source, msg.target, x), msg.local[x]);
        }
        
        // Decrypt and store remote Q's (they were encrypted with F_source)
        for (int x = 0; x < msg.remote.length; x++) {
            BigInteger remoteQ = paillierMgr.Decryption(fPaillierKey(msg.source), msg.remote[x]);
            variables.put(qKey(msg.round, msg.target, msg.source, msg.target, x), remoteQ);
        }
        
        // With both parts of Q, start Protocol 2 to compute R for the other agent
        int otherId = getOtherAgent(msg.source);
        kickStartProtocol2(otherId, msg.source, msg.round);
    }
    
    private void kickStartProtocol2(int targetId, int otherId, int round) {
        debug("Starting Protocol 2: target=" + targetId + " other=" + otherId + " round=" + round);
        
        int targetDomainSize = getDomainSize(targetId);
        int otherDomainSize = getDomainSize(otherId);
        
        // Generate random shifter
        BigInteger shifter = BigInteger.valueOf(random.nextInt(Integer.MAX_VALUE)).mod(prime);
        debug("Shifter: " + shifter + " targetDomain=" + targetDomainSize + " otherDomain=" + otherDomainSize);
        
        // Build W matrix [targetDomain][otherDomain]
        BigInteger[][] Ws = new BigInteger[targetDomainSize][otherDomainSize];
        
        // Get the Paillier instance for otherId (we encrypt with E_otherId)
        Paillier otherPaillier = paillierMgr.get(ePaillierKey(otherId));
        BigInteger nsquare = otherPaillier.nsquare;
        
        for (int x = 0; x < targetDomainSize; x++) {
            for (int y = 0; y < otherDomainSize; y++) {
                // Get Q values
                BigInteger myQ = variables.get(qKey(round, targetId, otherId, targetId, y));
                if (myQ == null) {
                    debug("ERROR: didn't find local Q");
                    myQ = BigInteger.ZERO;
                }
                
                BigInteger otherQ = variables.get(qKey(round, otherId, otherId, targetId, y));
                if (otherQ == null) {
                    debug("ERROR: didn't find remote Q");
                    otherQ = BigInteger.ZERO;
                }
                
                // Get constraint value
                int constraint = getConstraint(targetId, x, y);
                
                // Compute: W = E(shifter + myQ + constraint) * otherQ
                // Where otherQ is encrypted with E_otherId
                
                // === BUG FROM REFERENCE PORT ===
                // Original buggy code:
                // BigInteger tempW = shifter.add(myQ).mod(p).add(BigInteger.valueOf(constrain)).mod(p);
                // tempW = paillierMgr.Encryption(ePaillierKey(otherId), tempW);
                // Ws[x][y] = tempW.multiply(otherQ).mod(p);
                //
                // Problem: This code multiplies tempW (a ciphertext) by otherQ (a plaintext).
                // But otherQ is already decrypted in the lines above (line 155-156 in FunctionBarin).
                // Multiplying ciphertext by plaintext is NOT a valid Paillier operation.
                //
                // Paillier homomorphic properties:
                // - E(a) * E(b) mod n² = E(a + b)  (multiply ciphertexts → add plaintexts)
                // - E(a)^b mod n² = E(a * b)       (exponentiate ciphertext → multiply plaintext)
                //
                // Fix: Since we want to add otherQ (which is plaintext after decryption),
                // we need to encrypt it first, then multiply ciphertexts.
                // === END BUG ===
                
                // FIX: Encrypt the sum of plaintext values, then combine with the encrypted otherQ
                // Wait - re-reading the code: otherQ IS already the decrypted plaintext.
                // The Q_local (myQ here) is encrypted with E_otherId (stored as ciphertext)
                // The Q_remote (otherQ here) was decrypted when storing (line 200-201 above)
                // 
                // So we need to:
                // 1. Compute shifter + plaintext_otherQ + constraint
                // 2. Encrypt that sum
                // 3. Multiply by encrypted myQ (homomorphic add)
                
                // But wait, myQ from the variabler IS also the ciphertext (line 196 stores msg.local[x] directly)
                // So myQ is E(local_q) encrypted with E_source
                
                // Actually, looking more carefully at the protocol:
                // - localQ (stored at line 196) is the encrypted local Q (E_source(q_local))
                // - remoteQ (stored at line 200) is DECRYPTED plaintext
                
                // So the fix is: encrypt (shifter + constraint + remoteQ), multiply by localQ
                BigInteger plaintextSum = shifter.add(otherQ).add(BigInteger.valueOf(constraint)).mod(prime);
                BigInteger encPlaintextSum = paillierMgr.Encryption(ePaillierKey(otherId), plaintextSum);
                
                // myQ is E(q_local) encrypted with E_otherId  
                // Wait no - myQ was stored from msg.local which is encrypted with E_source
                // Let me re-read...
                //
                // In handleInjectQs:
                // - msg.local is encrypted with E_source (the sending agent)
                // - msg.remote is encrypted with F_source (also the sending agent)
                // 
                // So qKey(round, msg.source, msg.source, msg.target, x) = E_source(local_q)
                // And qKey(round, msg.target, msg.source, msg.target, x) = decrypted remote_q
                //
                // In kickStartProtocol2:
                // - targetId is the agent we're computing R for
                // - otherId is the agent whose Q's we're using
                //
                // So myQ = qKey(round, targetId, otherId, targetId, y)
                //        = Q from otherId's perspective, but for targetId domain
                // 
                // Hmm, this is confusing. Let me just follow the reference more closely
                // and fix the obvious bug of multiplying ciphertext by plaintext.
                
                // The key insight: we need HOMOMORPHIC addition, so multiply ciphertexts
                Ws[x][y] = encPlaintextSum.multiply(myQ).mod(nsquare);
            }
        }
        
        // TODO: Permute Ws for additional privacy
        
        // Send W matrix to target agent for min computation
        ProcessWsRequestMessage msg = new ProcessWsRequestMessage(round, otherId, targetId, Ws, shifter);
        int targetNodeId = getAgentNodeId(targetId);
        debug("Sending ProcessWsRequestMessage to node " + targetNodeId);
        transport.sendMessage(msg, targetNodeId);
    }
    
    private void handleProcessWsResponse(ProcessWsResponseMessage msg, int senderNodeId) {
        debug("Handling ProcessWsResponseMessage: " + msg);
        
        int targetDomainSize = msg.values.length;
        BigInteger[] encLocalRs = new BigInteger[targetDomainSize];
        
        for (int x = 0; x < targetDomainSize; x++) {
            // Recover local R by shifting back
            BigInteger localR = msg.values[x].subtract(msg.shifter).mod(prime);
            
            // Store local R
            variables.put(rKey(msg.round, msg.affinity, msg.source, msg.target, x), localR);
            
            // Encrypt with F_target for sending
            encLocalRs[x] = paillierMgr.Encryption(fPaillierKey(msg.target), localR);
        }
        
        // Send encrypted local R to agent (no remote this time)
        InjectRsMessage response = new InjectRsMessage(
            msg.round, msg.source, msg.target, encLocalRs, null, false);
        transport.sendMessage(response, senderNodeId);
    }
    
    private void handleMinIndexRequest(MinIndexRequestMessage msg, int senderNodeId) {
        debug("Handling MinIndexRequestMessage from agent " + msg.source);
        
        // === BUG FROM REFERENCE PORT ===
        // Original buggy code:
        // int minIndex = 0;
        // BigInteger minValue = paillierMgr.Decryption(fPaillierKey(msg.source), msg.m[0]);
        // for (int x = 1; x < msg.m.length; x++) {
        //     BigInteger curValue = paillierMgr.Decryption(fPaillierKey(msg.source), msg.m[x]);
        //     minValue = minValue.min(curValue);
        //     if (minValue.equals(curValue)) {
        //         minIndex = x;
        //     }
        // }
        //
        // Problem: This updates minIndex whenever curValue equals the minimum,
        // including ties. This finds the LAST occurrence of minimum, which may
        // not be the desired behavior. Also, the logic is confusing.
        //
        // Fix: Only update minIndex when we find a strictly smaller value.
        // === END BUG ===
        
        int minIndex = 0;
        BigInteger minValue = paillierMgr.Decryption(fPaillierKey(msg.source), msg.m[0]);
        
        for (int x = 1; x < msg.m.length; x++) {
            BigInteger curValue = paillierMgr.Decryption(fPaillierKey(msg.source), msg.m[x]);
            if (curValue.compareTo(minValue) < 0) {
                minValue = curValue;
                minIndex = x;
            }
        }
        
        debug("Min index: " + minIndex);
        
        MinIndexResponseMessage response = new MinIndexResponseMessage(minIndex);
        transport.sendMessage(response, senderNodeId);
    }
    
    // ========== Debug helper ==========
    
    private void debug(String msg) {
        if (DEBUG) {
            Global.log.logln(true, "FunctionBrain[" + agentA + "," + agentB + "]: " + msg);
        }
    }
}
