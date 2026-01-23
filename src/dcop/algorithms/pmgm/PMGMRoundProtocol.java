package dcop.algorithms.pmgm;

import dcop.algorithms.common.protocols.CostContributionHuddleProtocol;
import dcop.algorithms.common.protocols.ICostContributionHuddleListener;
import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.mpc.findmin.ISecureFindMinListener;
import utils.protocols.mpc.reconstruct.IReconstructListener;
import utils.protocols.mpc.distribution.IVectorShareDistributionListener;
import utils.protocols.mpc.dotproduct.ISecureDotProductListener;
import utils.protocols.mpc.securesub.ISecureSubListener;
import utils.protocols.mpc.copy.ISecureCopyShareListener;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.distribution.IShareDistributionListener;
import utils.protocols.mpc.findmax.ISecureFindMaxListener;
import utils.protocols.mpc.secureknownsub.ISecureKnownSubListener;
import utils.protocols.mpc.secureiszero.ISecureIsZeroListener;
import utils.protocols.mpc.secureadd.ISecureAddListener;
import utils.protocols.mpc.secureadd.SecureAddProtocol;
import utils.protocols.mpc.securesub.SecureSubProtocol;
import utils.protocols.mpc.secureiszero.SecureIsZeroProtocol;
import utils.protocols.mpc.secureknownsub.SecureKnownSubProtocol;
import utils.protocols.mpc.distribution.ShareDistributionProtocol;
import utils.protocols.mpc.distribution.VectorShareDistributionProtocol;
import utils.protocols.mpc.dotproduct.SecureDotProductProtocol;
import utils.protocols.mpc.findmax.SecureFindMaxProtocol;
import utils.protocols.mpc.copy.SecureCopyShareProtocol;
import utils.protocols.mpc.reconstruct.ReconstructSecretProtocol;
import utils.protocols.mpc.findmin.SecureFindMinProtocol;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * PMGMRoundProtocol - Orchestrates one complete round of P-MGM algorithm.
 * 
 * Baseline Phases:
 * 1. Share cost contributions (all agents help each other)
 * 2. Find best value using SecureFindMin
 * 3. Update value (always update to min)
 * 4. (Placeholder for PMGM-specific decision logic)
 * 
 * Unlike PDSA, PMGM does not have a stochastic decision phase.
 * Additional PMGM-specific logic will be added later.
 */
public class PMGMRoundProtocol implements IDistributedProtocol,
        ISecureFindMinListener, IReconstructListener, ICostContributionHuddleListener,
        IVectorShareDistributionListener, ISecureDotProductListener, ISecureSubListener,
        ISecureCopyShareListener, ISecureMultiplyListener, IShareDistributionListener,
        ISecureFindMaxListener, ISecureKnownSubListener, ISecureIsZeroListener, ISecureAddListener {
    
    public static final String PROTOCOL_TYPE = "PMGM_ROUND";
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first (cascades to their dependencies)
        CostContributionHuddleProtocol.registerFactory(manager);
        SecureFindMinProtocol.registerFactory(manager);
        SecureFindMaxProtocol.registerFactory(manager);
        SecureDotProductProtocol.registerFactory(manager);
        ReconstructSecretProtocol.registerFactory(manager);
        ShareDistributionProtocol.registerFactory(manager);
        VectorShareDistributionProtocol.registerFactory(manager);
        SecureAddProtocol.registerFactory(manager);
        SecureSubProtocol.registerFactory(manager);
        SecureMultiplyProtocol.registerFactory(manager);
        SecureKnownSubProtocol.registerFactory(manager);
        SecureIsZeroProtocol.registerFactory(manager);
        SecureCopyShareProtocol.registerFactory(manager);
        
        // PMGM_ROUND: initiator only (each agent creates their own round)
        manager.registerProtocolFactory(PROTOCOL_TYPE, () -> new PMGMRoundProtocol(), null);
    }
    
    // Debug logging flag
    private static final boolean DEBUG = false;
    private static final int DEBUG_AGENT_ID = -1; // -1 = all agents, or specific agent ID
    
    private void debug(String message) {
        if (DEBUG && (DEBUG_AGENT_ID == -1 || agentID == DEBUG_AGENT_ID)) {
            System.out.println(message);
        }
    }
    
    private void panic(String message) {
        throw new RuntimeException("PMGM FATAL ERROR [Agent " + agentID + "]: " + message);
    }
    
    // Context from agent
    private int agentID;
    private int initialValue;
    private Map<Integer, int[][]> constraintMatrices;
    private Random cryptoRandom;
    private long prime;
    private int s;  // Exponent for Mersenne prime: prime = 2^s - 1
    private int roundNumber;
    private IPMGMRoundListener listener;
    
    // Protocol state
    private String protocolId;
    private IMessageTransport transport;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    
    // Phase tracking
    private enum Phase {
        INITIALIZING,
        SHARING,              // Phase 1: Cost contribution collection
        FINDING_BEST,         // Phase 2: SecureFindMin
        DISTRIBUTING_E,       // Phase 3.1: Distribute E vector
        COMPUTING_CURRENT_COST, // Phase 3.2: SecureDotProduct(Wb, E) -> currentCost
        COMPUTING_GAIN,       // Phase 3.3: SecureSub(currentCost, minCost) -> gain
        COMPUTING_RELEVANT_GAINS, // Phase 4: Build relevant gain vector g_{i}[j] for all j
        FINDING_MAX_GAIN,     // Phase 5: SecureFindMax on relevant gains
        DECIDING,             // Phase 6: Secure decision (6 steps)
        RECONSTRUCTING,       // Phase 7: Reconstruct finalValue
        COMPLETE
    }
    private Phase currentPhase;
    
    // Domain size (cached after first computation)
    private int domainSize;
    
    // Relevant gains tracking (Phase 4)
    // Total operations: N copies (diagonal) + (N-1) multiplies (non-diagonal) = 2N-1
    private int relevantGainsOperationsRemaining;
    
    // Parallel Phase 1 tracking: cost collection + currValue distribution
    private boolean costCollectionComplete;
    private boolean currValueDistributed;
    
    // Phase 6 decision step tracking (steps 1-6)
    private int decisionStep;
    
    // Result
    private int newValue;
    private boolean complete;
    private boolean successful;
    
    /**
     * Default constructor for factory pattern.
     */
    public PMGMRoundProtocol() {
        this.currentPhase = Phase.INITIALIZING;
        this.complete = false;
        this.successful = false;
    }
    
    /**
     * Constructor with full context (for initiator).
     */
    public PMGMRoundProtocol(
            int agentID,
            int initialValue,
            Map<Integer, int[][]> constraintMatrices,
            Random algorithmRandom,
            Random cryptoRandom,
            long prime,
            int roundNumber,
            IPMGMRoundListener listener) {
        
        this();
        
        this.agentID = agentID;
        this.initialValue = initialValue;
        this.newValue = initialValue;  // Default: no change
        this.constraintMatrices = constraintMatrices;
        this.cryptoRandom = cryptoRandom;
        this.prime = prime;
        this.s = Long.numberOfTrailingZeros(prime + 1);  // For prime = 2^s - 1
        this.roundNumber = roundNumber;
        this.listener = listener;
        this.currentPhase = Phase.INITIALIZING;
    }
    
    /**
     * Computes the protocol ID for a PMGM round.
     * 
     * @param agentId The agent ID
     * @param roundNumber The round number
     * @return The protocol ID
     */
    public static String computeProtocolId(int agentId, int roundNumber) {
        return "pmgm-round-" + roundNumber + "-agent" + agentId;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract infrastructure parameters (protocol manager provides these)
        this.protocolId = (String) params.get("protocolId");
        this.transport = (IMessageTransport) params.get("transport");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.participants = (List) params.get("participants");
        
        // Share storage - from params (agent provides) or may be set in constructor
        if (params.containsKey("shareStorage")) {
            this.shareStorage = (IShareStorage) params.get("shareStorage");
        }
        
        // Round number - from params for sub-protocol tracking
        if (params.containsKey("roundNumber") && this.roundNumber == 0) {
            this.roundNumber = (Integer) params.get("roundNumber");
        }
        
        // Note: Domain-specific params (constraintMatrices, algorithmRandom, cryptoRandom,
        // prime, initialValue, listener) are passed via constructor injection.
        // Round protocols are always created by the agent directly, never by factory.
        
        // Start Phase 1 if all required state is available
        if (participants != null && constraintMatrices != null) {
            debug("Agent " + agentID + " starting round " + roundNumber + " with value=" + initialValue);
            currentPhase = Phase.SHARING;
            startCostCollector();
        } else {
            debug("PMGMRoundProtocol: Missing required state, waiting for initialization");
        }
    }
    
    // PHASE 1: COST CONTRIBUTION COLLECTION + CURRENT VALUE DISTRIBUTION
    
    private void startCostCollector() {
        debug("Agent " + agentID + " starting Phase 1: Cost Collection + CurrValue Distribution (round " + roundNumber + ")");
        
        // Reset parallel tracking
        costCollectionComplete = false;
        currValueDistributed = false;
        
        // Start cost contribution huddle
        String huddleId = CostContributionHuddleProtocol.computeProtocolId(roundNumber);
        CostContributionHuddleProtocol huddle = new CostContributionHuddleProtocol(
            agentID, initialValue, constraintMatrices,
            cryptoRandom, prime, roundNumber,
            shareStorage, this
        );
        
        Map<String, Object> huddleParams = new HashMap<>();
        huddleParams.put("protocolId", huddleId);
        
        manager.startProtocol(huddle, huddleParams, participants);
        
        // Start current value distribution (in parallel)
        // This creates a secret share of initialValue so we can use it in Phase 6
        distributeCurrValue();
    }
    
    /**
     * Gets the secret ID for the current value share.
     */
    private String getCurrValueId() {
        return "currValue_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Distributes the current value (initialValue) as a secret share.
     * This runs in parallel with cost collection.
     */
    private void distributeCurrValue() {
        debug("Agent " + agentID + " distributing currValue=" + initialValue);
        
        ShareDistributionProtocol.start(
            manager,
            (long) initialValue,
            getCurrValueId(),
            participants.size() / 2,
            prime,
            participants,
            shareStorage,
            getRoundTag(),  // Scope shares to this round
            this
        );
    }
    
    @Override
    public void onHuddleComplete(String huddleProtocolId, int targetAgent, int huddleRoundNumber) {
        if (huddleRoundNumber != roundNumber) {
            panic("Wrong round in huddle complete: expected " + roundNumber + ", got " + huddleRoundNumber);
            return;
        }
        
        costCollectionComplete = true;
        debug("Agent " + agentID + " cost collection complete");
        
        checkPhase1Complete();
    }
    
    @Override
    public void onShareDistributionComplete(String protocolId, String secretId) {
        if (currentPhase != Phase.SHARING) {
            debug("Agent " + agentID + " IGNORING ShareDistribution callback - wrong phase");
            return;
        }
        
        currValueDistributed = true;
        debug("Agent " + agentID + " currValue distribution complete");
        
        checkPhase1Complete();
    }
    
    /**
     * Checks if both Phase 1 operations (cost collection + currValue distribution) are complete.
     */
    private void checkPhase1Complete() {
        if (!costCollectionComplete || !currValueDistributed) {
            return;
        }
        
        debug("Agent " + agentID + " Phase 1 fully complete - proceeding to Phase 2");
        findBestValue();
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // Cost contributions are handled by CostContributionHuddleProtocol
        // No messages are expected directly by PMGMRoundProtocol
    }
    
    // PHASE 2: FIND BEST VALUE
    
    private void findBestValue() {
        currentPhase = Phase.FINDING_BEST;
        debug("Agent " + agentID + " Phase 2: Find best value");
        
        // Get and cache domain size
        this.domainSize = 0;
        for (int[][] matrix : constraintMatrices.values()) {
            if (matrix != null && matrix.length > 0) {
                this.domainSize = matrix[0].length;
                break;
            }
        }
        
        if (domainSize == 0) {
            debug("Agent " + agentID + " no constraints, completing round");
            completeRound();
            return;
        }
        
        // Verify shares exist
        String baseArrayId = getWbBaseId();
        for (int i = 0; i < domainSize; i++) {
            String shareId = baseArrayId + "[" + i + "]";
            if (shareStorage.getShare(shareId) == null) {
                panic("Missing share: " + shareId);
            }
        }
        
        debug("Agent " + agentID + " starting SecureFindMin for " + baseArrayId);
        
        SecureFindMinProtocol.start(
            manager,
            baseArrayId,
            "minCost_" + agentID + "-r" + roundNumber,
            "bestValue_" + agentID + "-r" + roundNumber,
            0,
            domainSize - 1,
            "r-key",
            prime,
            participants.size() / 2,
            participants,
            shareStorage,
            getRoundTag(),
            this
        );
    }
    
    @Override
    public void onSecureFindMinComplete(String protocolId, String valueId, String indexId) {
        if (currentPhase != Phase.FINDING_BEST) {
            debug("Agent " + agentID + " IGNORING FindMin callback - wrong phase");
            return;
        }
        
        debug("Agent " + agentID + " Phase 3.1: Distribute E vector (selector for initialValue=" + initialValue + ")");
        distributeEVector();
    }
    
    // PHASE 3: COMPUTE GAIN
    // gain = currentCost - minCost
    // currentCost = Wb · E (dot product of cost vector with selector)
    // E = [0, 0, ..., 1, ..., 0] where 1 is at position initialValue
    
    /**
     * Gets the storage tag for this round's shares.
     * Used to scope shares so they can be cleaned up after the round.
     */
    private String getRoundTag() {
        return "round-" + roundNumber;
    }
    
    /**
     * Gets the base secret ID for the E vector.
     * E is the selector vector: E[i] = 1 if i == initialValue, else 0
     */
    private String getEBaseId() {
        return "E_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for the current cost (result of Wb · E).
     */
    private String getCurrentCostId() {
        return "currentCost_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for the gain (currentCost - minCost).
     */
    private String getGainId() {
        return "gain_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for the min cost (from FindMin).
     */
    private String getMinCostId() {
        return "minCost_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the base secret ID for the Wb vector.
     */
    private String getWbBaseId() {
        return "Wb_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Phase 3.1: Create and distribute the E vector (selector vector).
     * E[i] = 1 if i == initialValue, else 0
     */
    private void distributeEVector() {
        currentPhase = Phase.DISTRIBUTING_E;
        
        // Create E vector: all zeros except 1 at initialValue
        long[] eValues = new long[domainSize];
        for (int i = 0; i < domainSize; i++) {
            eValues[i] = (i == initialValue) ? 1L : 0L;
        }
        
        debug("Agent " + agentID + " distributing E vector: E[" + initialValue + "]=1, others=0");
        
        VectorShareDistributionProtocol.start(
            manager,
            getEBaseId(),
            eValues,
            prime,
            participants,
            shareStorage,
            getRoundTag(),  // Scope shares to this round
            this
        );
    }
    
    @Override
    public void onVectorShareDistributionComplete(String protocolId, String baseSecretId) {
        if (currentPhase != Phase.DISTRIBUTING_E) {
            debug("Agent " + agentID + " IGNORING VectorShareDistribution callback - wrong phase");
            return;
        }
        
        debug("Agent " + agentID + " Phase 3.2: Computing currentCost = Wb · E");
        computeCurrentCost();
    }
    
    /**
     * Phase 3.2: Compute currentCost = Wb · E using SecureDotProduct.
     * This gives the cost if we keep the current (initial) value.
     */
    private void computeCurrentCost() {
        currentPhase = Phase.COMPUTING_CURRENT_COST;
        
        SecureDotProductProtocol.start(
            manager,
            getWbBaseId(),  // Vector A: Wb (cost vector)
            getEBaseId(),   // Vector B: E (selector)
            getCurrentCostId(),  // Output: currentCost
            domainSize,
            "r-key",  // Pre-distributed random secret for SecureMultiply
            prime,
            participants,
            shareStorage,
            getRoundTag(),  // Scope shares to this round
            this
        );
    }
    
    @Override
    public void onSecureDotProductComplete(String protocolId, String resultId) {
        if (currentPhase != Phase.COMPUTING_CURRENT_COST) {
            debug("Agent " + agentID + " IGNORING SecureDotProduct callback - wrong phase");
            return;
        }
        
        debug("Agent " + agentID + " Phase 3.3: Computing gain = currentCost - minCost");
        computeGain();
    }
    
    /**
     * Phase 3.3: Compute gain = currentCost - minCost using SecureSub.
     * Positive gain means switching to bestValue is beneficial.
     */
    private void computeGain() {
        currentPhase = Phase.COMPUTING_GAIN;
        
        SecureSubProtocol.start(
            manager,
            getCurrentCostId(),  // A: currentCost
            getMinCostId(),      // B: minCost
            getGainId(),         // Output: gain = currentCost - minCost
            prime,
            participants,
            shareStorage,
            getRoundTag(),       // Scope to this round
            this
        );
    }
    
    // PHASE 4: COMPUTE RELEVANT GAINS
    // Each agent computes a relevant gain vector g_{i}[j] for all j in participants
    // g_{i}[j] = SecureMultiply(gain_{j}, n_{i}_{j}) for j != i
    //          = gain[j] if i,j connected (n_i_j=1), or 0 if not connected (n_i_j=0)
    // g_{i}[i] = SecureCopyShare(gain_{i}) (own gain always relevant)
    
    /**
     * Gets the base secret ID for this agent's gain vector.
     */
    private String getGainVectorBaseId() {
        return "g_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for entry j in this agent's gain vector.
     */
    private String getGainVectorEntryId(int j) {
        return getGainVectorBaseId() + "[" + j + "]";
    }
    
    /**
     * Gets the topology secret ID for connection between agents i and j.
     */
    private String getTopologySecretId(int i, int j) {
        return "n_" + i + "_" + j;
    }
    
    /**
     * Gets the gain secret ID for agent j.
     */
    private String getAgentGainId(int j) {
        return "gain_" + j + "-r" + roundNumber;
    }
    
    /**
     * Phase 4: Compute the relevant gain vector.
     * 
     * For each participant j:
     * - If j == agentID: SecureCopyShare(gain_{agentID} -> g_{agentID}[agentID])
     * - If j != agentID: SecureMultiply(gain_{j}, n_{agentID}_{j}) -> g_{agentID}[j]
     *   Result is gain[j] if connected, 0 if not connected
     * 
     * All operations run in parallel. Total: N copies + (N-1) multiplies = 2N-1
     */
    private void computeRelevantGains() {
        currentPhase = Phase.COMPUTING_RELEVANT_GAINS;
        
        int n = participants.size();
        // Total operations: N copies (for all diagonal entries) + (N-1) multiplies (for non-diagonal)
        relevantGainsOperationsRemaining = n + (n - 1);
        
        debug("Agent " + agentID + " Phase 4: Computing relevant gains (" + relevantGainsOperationsRemaining + " operations)");
        
        // Start N SecureCopyShare for all diagonal entries g_{k}[k] for all k
        // Each agent initiates the copy for all diagonal entries
        for (int k : participants) {
            String srcId = getAgentGainId(k);
            String dstId = "g_" + k + "-r" + roundNumber + "[" + k + "]";
            
            debug("Agent " + agentID + " starting copy: " + srcId + " -> " + dstId);
            
            SecureCopyShareProtocol.start(
                manager,
                srcId,
                dstId,
                participants,
                shareStorage,
                this
            );
        }
        
        // Start (N-1) SecureMultiply for non-diagonal entries g_{agentID}[j] for j != agentID
        for (int j : participants) {
            if (j != agentID) {
                String gainJId = getAgentGainId(j);
                String topologyId = getTopologySecretId(agentID, j);
                String outputId = getGainVectorEntryId(j);
                
                debug("Agent " + agentID + " starting multiply: " + gainJId + " * " + topologyId + " -> " + outputId);
                
                SecureMultiplyProtocol.start(
                    manager,
                    gainJId,
                    topologyId,
                    outputId,
                    "r-key",
                    prime,
                    participants,
                    shareStorage,
                    getRoundTag(),   // Scope to this round
                    this
                );
            }
        }
    }
    
    @Override
    public void onSecureCopyShareComplete(String protocolId, String srcSecretId, String dstSecretId) {
        if (currentPhase != Phase.COMPUTING_RELEVANT_GAINS) {
            debug("Agent " + agentID + " IGNORING CopyShare callback - wrong phase");
            return;
        }
        
        relevantGainsOperationsRemaining--;
        debug("Agent " + agentID + " copy complete: " + srcSecretId + " -> " + dstSecretId + 
              " (" + relevantGainsOperationsRemaining + " remaining)");
        
        checkRelevantGainsComplete();
    }
    
    private void checkRelevantGainsComplete() {
        if (relevantGainsOperationsRemaining <= 0) {
            debug("Agent " + agentID + " relevant gains complete, proceeding to Phase 5");
            
            // At this point we have:
            // - g_{agentID}[j] for all j in participants (agent's relevant gain vector)
            //   Each entry is gain[j] if connected, 0 if not connected
            // - All diagonal entries g_{k}[k] for all k (own gains)
            
            findMaxRelevantGain();
        }
    }
    
    // PHASE 5: FIND MAXIMUM RELEVANT GAIN
    // Find which agent in our neighborhood has the maximum gain
    
    /**
     * Gets the secret ID for the maximum gain value.
     */
    private String getMaxGainId() {
        return "maxGain_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for the agent with maximum gain.
     */
    private String getMaxGainAgentId() {
        return "maxGainAgent_" + agentID + "-r" + roundNumber;
    }
    
    private void findMaxRelevantGain() {
        currentPhase = Phase.FINDING_MAX_GAIN;
        
        int firstIdx = participants.get(0);
        int lastIdx = participants.get(participants.size() - 1);
        
        debug("Agent " + agentID + " Phase 5: Finding max relevant gain in range [" + firstIdx + ", " + lastIdx + "]");
        
        SecureFindMaxProtocol.start(
            manager,
            getGainVectorBaseId(),
            getMaxGainId(),
            getMaxGainAgentId(),
            firstIdx,
            lastIdx,
            "r-key",
            prime,
            participants.size() / 2,
            participants,
            shareStorage,
            getRoundTag(),
            this
        );
    }
    
    @Override
    public void onSecureFindMaxComplete(String protocolId, String valueResultId, String indexResultId) {
        if (currentPhase != Phase.FINDING_MAX_GAIN) {
            debug("Agent " + agentID + " IGNORING FindMax callback - wrong phase");
            return;
        }
        
        debug("Agent " + agentID + " Phase 5 complete - proceeding to reconstruct maxGainAgent for debug");
        
        // DEBUG: Reconstruct maxGainAgent to see its actual value
        currentPhase = Phase.DECIDING; // Temporarily advance phase for debug
        ReconstructSecretProtocol.start(
            manager,
            getMaxGainAgentId(),
            prime,
            participants,
            shareStorage,
            new utils.protocols.mpc.reconstruct.IReconstructListener() {
                @Override
                public void onReconstructComplete(String pid, String sid, long reconstructedValue) {
                    debug("Agent " + agentID + " DEBUG: maxGainAgent reconstructed to " + reconstructedValue + 
                          " (agent ID is " + agentID + ", equal=" + (reconstructedValue == agentID) + ")");
                    // Now proceed with decision
                    startDecision();
                }
            }
        );
    }
    
    // PHASE 6: SECURE DECISION
    // Compute finalValue = currValue + isMax * (bestValue - currValue)
    // Step 1: diff = SecureKnownSub(maxGainAgent, agentID)
    // Step 2: isMax = SecureIsZero(diff)
    // Step 3: valueDiff = SecureSub(bestValue, currValue)
    // Step 4: tmp = SecureMultiply(isMax, valueDiff)
    // Step 5: finalValue = SecureAdd(currValue, tmp)
    // Step 6: Reconstruct(finalValue) -> newValue
    
    /**
     * Gets the secret ID for the diff (maxGainAgent - agentID).
     */
    private String getDiffId() {
        return "diff_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for the isMax flag.
     */
    private String getIsMaxId() {
        return "isMax_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for valueDiff (bestValue - currValue).
     */
    private String getValueDiffId() {
        return "valueDiff_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for tmp (isMax * valueDiff).
     */
    private String getTmpId() {
        return "tmp_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for finalValue (currValue + tmp).
     */
    private String getFinalValueId() {
        return "finalValue_" + agentID + "-r" + roundNumber;
    }
    
    /**
     * Gets the secret ID for bestValue (from Phase 2).
     */
    private String getBestValueId() {
        return "bestValue_" + agentID + "-r" + roundNumber;
    }
    
    private void startDecision() {
        currentPhase = Phase.DECIDING;
        decisionStep = 1;
        
        debug("Agent " + agentID + " Phase 6: Starting decision (step 1/6)");
        
        // Step 1: diff = SecureKnownSub(maxGainAgent, agentID)
        SecureKnownSubProtocol.start(
            manager,
            (long) agentID,
            getMaxGainAgentId(),
            getDiffId(),
            false,  // knownIsLeft = false → maxGainAgent - agentID
            prime,
            participants,
            shareStorage,
            getRoundTag(),   // Scope to this round
            this
        );
    }
    
    @Override
    public void onSecureKnownSubComplete(String protocolId, String resultId) {
        if (currentPhase != Phase.DECIDING || decisionStep != 1) {
            debug("Agent " + agentID + " IGNORING KnownSub callback - wrong phase/step");
            return;
        }
        
        // Debug: Print the diff share
        Share diffShare = shareStorage.getShare(getDiffId());
        debug("Agent " + agentID + " diff share value=" + (diffShare != null ? diffShare.getValue() : "null") + 
              " (diff = maxGainAgent - " + agentID + ")");
        
        decisionStep = 2;
        debug("Agent " + agentID + " Decision step 2/6: SecureIsZero");
        
        // Step 2: isMax = SecureIsZero(diff)
        // Using Fermat's Little Theorem: isZero(x) = 1 - x^(p-1)
        SecureIsZeroProtocol.start(
            manager,
            getDiffId(),
            getIsMaxId(),
            "r-key",
            prime,
            s,  // Exponent for Mersenne prime
            participants,
            shareStorage,
            getRoundTag(),  // Scope shares to this round
            this
        );
    }
    
    @Override
    public void onSecureIsZeroComplete(String protocolId, String resultSecretId) {
        if (currentPhase != Phase.DECIDING || decisionStep != 2) {
            debug("Agent " + agentID + " IGNORING IsZero callback - wrong phase/step");
            return;
        }
        
        decisionStep = 3;
        debug("Agent " + agentID + " Decision step 3/6: SecureSub(bestValue, currValue)");
        
        // Step 3: valueDiff = SecureSub(bestValue, currValue)
        SecureSubProtocol.start(
            manager,
            getBestValueId(),
            getCurrValueId(),
            getValueDiffId(),
            prime,
            participants,
            shareStorage,
            getRoundTag(),   // Scope to this round
            this
        );
    }
    
    @Override
    public void onSecureSubComplete(String protocolId, String resultId) {
        if (currentPhase == Phase.COMPUTING_GAIN) {
            // This is from Phase 3.3 (computing gain)
            debug("Agent " + agentID + " gain computation complete, proceeding to Phase 4 (Relevant Gains)");
            computeRelevantGains();
        } else if (currentPhase == Phase.DECIDING && decisionStep == 3) {
            decisionStep = 4;
            debug("Agent " + agentID + " Decision step 4/6: SecureMultiply(isMax, valueDiff)");
            
            
            // Step 4: tmp = SecureMultiply(isMax, valueDiff)
            SecureMultiplyProtocol.start(
                manager,
                getIsMaxId(),
                getValueDiffId(),
                getTmpId(),
                "r-key",
                prime,
                participants,
                shareStorage,
                getRoundTag(),   // Scope to this round
                this
            );
        } else {
            debug("Agent " + agentID + " IGNORING SecureSub callback - wrong phase/step");
        }
    }
    
    @Override
    public void onSecureMultiplyComplete(String protocolId, String resultId) {
        if (currentPhase == Phase.COMPUTING_RELEVANT_GAINS) {
            // This is from Phase 4 (computing relevant gains)
            relevantGainsOperationsRemaining--;
            debug("Agent " + agentID + " multiply complete: " + resultId + 
                  " (" + relevantGainsOperationsRemaining + " remaining)");
            checkRelevantGainsComplete();
        } else if (currentPhase == Phase.DECIDING && decisionStep == 4) {
            decisionStep = 5;
            debug("Agent " + agentID + " Decision step 5/6: SecureAdd(currValue, tmp)");
            
            // Step 5: finalValue = SecureAdd(currValue, tmp)
            SecureAddProtocol.start(
                manager,
                getCurrValueId(),
                getTmpId(),
                getFinalValueId(),
                prime,
                participants,
                shareStorage,
                getRoundTag(),  // Scope to this round
                this
            );
        } else {
            debug("Agent " + agentID + " IGNORING Multiply callback - wrong phase/step");
        }
    }
    
    @Override
    public void onSecureAddComplete(String protocolId, String resultId) {
        if (currentPhase != Phase.DECIDING || decisionStep != 5) {
            debug("Agent " + agentID + " IGNORING SecureAdd callback - wrong phase/step");
            return;
        }
        
        decisionStep = 6;
        
        // Debug: Print shares for finalValue computation
        Share finalShare = shareStorage.getShare(getFinalValueId());
        Share bestShare = shareStorage.getShare(getBestValueId());
        Share currShare = shareStorage.getShare(getCurrValueId());
        Share tmpShare = shareStorage.getShare(getTmpId());
        debug("Agent " + agentID + " Decision step 6/6: Reconstruct(finalValue)");
        debug("Agent " + agentID + " Shares: bestValue=" + (bestShare != null ? bestShare.getValue() : "null") +
              ", currValue=" + (currShare != null ? currShare.getValue() : "null") +
              ", tmp=" + (tmpShare != null ? tmpShare.getValue() : "null") +
              ", finalValue=" + (finalShare != null ? finalShare.getValue() : "null"));
        
        // Step 6: Reconstruct(finalValue) -> newValue
        currentPhase = Phase.RECONSTRUCTING;
        
        ReconstructSecretProtocol.start(
            manager,
            getFinalValueId(),
            prime,
            participants,
            shareStorage,
            this
        );
    }
    
    // PHASE 7: RECONSTRUCT AND COMPLETE
    
    @Override
    public void onReconstructComplete(String protocolId, String secretId, long reconstructedValue) {
        if (currentPhase != Phase.RECONSTRUCTING) {
            debug("Agent " + agentID + " IGNORING Reconstruct callback - wrong phase");
            return;
        }
        
        newValue = (int) reconstructedValue;
        
        debug("Agent " + agentID + " reconstructed finalValue=" + newValue + 
              " (initialValue was " + initialValue + ", changed=" + (newValue != initialValue) + ")");
        
        completeRound();
    }
    
    private void completeRound() {
        currentPhase = Phase.COMPLETE;
        complete = true;
        successful = true;
        
        debug("Agent " + agentID + " ROUND " + roundNumber + " COMPLETE: " + 
              initialValue + " -> " + newValue + " (changed=" + (newValue != initialValue) + ")");
        
        if (listener != null) {
            listener.onRoundComplete(this.protocolId, roundNumber, newValue);
        }
    }
    
    @Override
    public boolean isComplete() {
        return complete;
    }
    
    @Override
    public boolean isSuccessful() {
        return successful;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return "PMGM_ROUND";
    }
    
    @Override
    public Object getResult() {
        return newValue;
    }
}

