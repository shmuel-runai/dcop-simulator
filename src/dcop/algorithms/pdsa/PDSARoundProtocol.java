package dcop.algorithms.pdsa;

import dcop.algorithms.common.protocols.CostContributionHuddleProtocol;
import dcop.algorithms.common.protocols.ICostContributionHuddleListener;
import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.mpc.findmin.ISecureFindMinListener;
import utils.protocols.mpc.reconstruct.IReconstructListener;
import utils.protocols.mpc.reconstruct.ReconstructSecretProtocol;
import utils.protocols.mpc.findmin.SecureFindMinProtocol;
import utils.crypto.secretsharing.IShareStorage;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * PDSARoundProtocol - Orchestrates one complete round of P-DSA algorithm.
 * 
 * Phases:
 * 1. Share cost contributions (all agents help each other)
 * 2. Stochastic decision (early exit if random says no)
 * 3. Find best value using SecureFindMin (conditional)
 * 4. Update value (conditional)
 * 5. Signal completion
 * 6. Synchronization & cleanup
 */
public class PDSARoundProtocol implements IDistributedProtocol,
        ISecureFindMinListener, IReconstructListener, ICostContributionHuddleListener {
    
    public static final String PROTOCOL_TYPE = "PDSA_ROUND";
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first (cascades to their dependencies)
        CostContributionHuddleProtocol.registerFactory(manager);
        SecureFindMinProtocol.registerFactory(manager);
        ReconstructSecretProtocol.registerFactory(manager);
        
        // PDSA_ROUND: initiator only (each agent creates their own round)
        manager.registerProtocolFactory(PROTOCOL_TYPE, () -> new PDSARoundProtocol(), null);
    }
    
    // Debug logging flag - set to true to enable verbose output
    private static final boolean DEBUG = false;
    private static final int DEBUG_AGENT_ID = 7; // -1 = all agents, or set to specific agent ID
    
    private void debug(String message) {
        if (DEBUG && (DEBUG_AGENT_ID == -1 || agentID == DEBUG_AGENT_ID)) {
            System.out.println(message);
        }
    }
    
    private void panic(String message) {
        throw new RuntimeException("PDSA FATAL ERROR [Agent " + agentID + "]: " + message);
    }
    
    
    // Context from agent (non-final for factory pattern support)
    private int agentID;
    private int initialValue;
    private Map<Integer, int[][]> constraintMatrices;
    private double stochastic;
    private Random algorithmRandom;
    private Random cryptoRandom;
    private long prime;
    private int roundNumber;
    private IPDSARoundListener listener;
    
    // Protocol state
    private String protocolId;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    
    // Phase tracking
    private enum Phase {
        INITIALIZING,
        SHARING,
        DECIDING,
        FINDING_BEST,
        UPDATING,
        COMPLETE
    }
    private Phase currentPhase;
    
    // Phase 1: Cost collection (delegated to CostContributionHuddleProtocol)
    
    // Phase 4 result
    private int newValue;
    
    // Completion state
    private boolean complete;
    private boolean successful;
    
    /**
     * Default constructor for factory pattern (used when creating from incoming messages).
     */
    public PDSARoundProtocol() {
        // Will be initialized via initialize() method
        this.complete = false;
        this.successful = false;
        this.currentPhase = Phase.INITIALIZING;
    }
    
    /**
     * Creates a new P-DSA round protocol (used by initiator).
     */
    public PDSARoundProtocol(
            int agentID,
            int initialValue,
            Map<Integer, int[][]> constraintMatrices,
            double stochastic,
            Random algorithmRandom,
            Random cryptoRandom,
            long prime,
            int roundNumber,
            IPDSARoundListener listener) {
        
        this();  // Call default constructor first
        
        this.agentID = agentID;
        this.initialValue = initialValue;
        this.newValue = initialValue;  // Default: no change
        this.constraintMatrices = constraintMatrices;
        this.stochastic = stochastic;
        this.algorithmRandom = algorithmRandom;
        this.cryptoRandom = cryptoRandom;
        this.prime = prime;
        this.roundNumber = roundNumber;
        this.listener = listener;
        this.currentPhase = Phase.INITIALIZING;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract infrastructure parameters (protocol manager provides these)
        this.protocolId = (String) params.get("protocolId");
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
        // stochastic, prime, initialValue, listener) are passed via constructor injection.
        // Round protocols are always created by the agent directly, never by factory.
        
        // Start Phase 1 if all required state is available
        if (participants != null && constraintMatrices != null) {
            debug("Agent " + agentID + " starting round " + roundNumber + " with value=" + initialValue);
            currentPhase = Phase.SHARING;
            startCostCollector();
        } else {
            debug("PDSARoundProtocol: Missing required state, waiting for initialization");
        }
    }
    
    // PHASE 1: START COST CONTRIBUTION COLLECTORS
    
    /**
     * Starts the cost contribution collector for this round.
     * One collector handles ALL targets - it sends shares for other agents
     * and waits for ready messages for this agent's target.
     */
    private void startCostCollector() {
        debug("Agent " + agentID + " starting Phase 1: Cost Collection (round " + roundNumber + ")");
        
        String huddleId = CostContributionHuddleProtocol.computeProtocolId(roundNumber);
        CostContributionHuddleProtocol huddle = new CostContributionHuddleProtocol(
            agentID, initialValue, constraintMatrices,
            cryptoRandom, prime, roundNumber,
            shareStorage, this
        );
        
        Map<String, Object> huddleParams = new HashMap<>();
        huddleParams.put("protocolId", huddleId);
        
        manager.startProtocol(huddle, huddleParams, participants);
    }
    
    @Override
    public void onHuddleComplete(String huddleProtocolId, int targetAgent, int huddleRoundNumber) {
        if (huddleRoundNumber != roundNumber) {
            panic("Wrong round in huddle complete: expected " + roundNumber + ", got " + huddleRoundNumber);
            return;
        }
        
        debug("Agent " + agentID + " cost collection complete - proceeding to Phase 2");
        
        // Phase 1 complete - proceed to Phase 2 (stochastic decision)
        performStochasticDecision();
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // Cost contributions are handled by CostContributionHuddleProtocol
        // Synchronization is handled by BarrierProtocol at the agent level
        // No messages are expected directly by PDSARoundProtocol
    }
    
    // PHASE 2: STOCHASTIC DECISION
    private void performStochasticDecision() {
        currentPhase = Phase.DECIDING;
        debug("Agent " + agentID + " Phase 2: Stochastic decision");
        
        // Use algorithmRandom for stochastic decision
        double randomValue = algorithmRandom.nextDouble();
        boolean willTryChange = (randomValue < stochastic);
        
        debug("Agent " + agentID + " stochastic: rand=" + randomValue + ", threshold=" + stochastic + ", willTry=" + willTryChange);
        
        if (!willTryChange) {
            // Not trying to change - round complete with current value
            debug("Agent " + agentID + " decided NOT to try change - completing round");
            completeRound();
        } else {
            // Proceed to Phase 3
            debug("Agent " + agentID + " decided to try change - proceeding to Phase 3");
            findBestValue();
        }
    }
    
    // PHASE 3: FIND BEST VALUE
    private void findBestValue() {
        currentPhase = Phase.FINDING_BEST;
        debug("Agent " + agentID + " Phase 3: Find best value");
        
        // Get domain size (from first row of any constraint matrix)
        int domainSize = 0;
        for (int[][] matrix : constraintMatrices.values()) {
            if (matrix != null && matrix.length > 0) {
                domainSize = matrix[0].length;
                break;
            }
        }
        
        debug("Agent " + agentID + " domainSize=" + domainSize);
        
        if (domainSize == 0) {
            // No constraints - round complete with current value
            debug("Agent " + agentID + " no constraints, completing round");
            completeRound();
            return;
        }
        
        // SAFETY CHECK: Verify shares exist before starting FindMin
        String baseArrayId = "Wb_" + agentID + "-r" + roundNumber;
        boolean sharesExist = true;
        for (int i = 0; i < domainSize; i++) {
            String shareId = baseArrayId + "[" + i + "]";
            if (shareStorage.getShare(shareId) == null) {
                panic("Missing share: " + shareId);
                sharesExist = false;
            }
        }
        
        if (!sharesExist) {
            panic("Cannot start FindMin - shares not ready!");
        }
        
        debug("Agent " + agentID + " starting SecureFindMin for " + baseArrayId + " (domainSize=" + domainSize + ")");
        
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
            this  // Listener
        );
    }
    
    @Override
    public void onSecureFindMinComplete(String protocolId, String valueId, String indexId) {
        debug("Agent " + agentID + " onSecureFindMinComplete: phase=" + currentPhase + ", valueId=" + valueId);
        if (currentPhase != Phase.FINDING_BEST) {
            debug("Agent " + agentID + " IGNORING FindMin callback - wrong phase");
            return;
        }
        
        // Proceed to Phase 4
        debug("Agent " + agentID + " Phase 4: Reconstruct best value");
        reconstructBestValue();
    }
    
    // PHASE 4: UPDATE VALUE
    private void reconstructBestValue() {
        currentPhase = Phase.UPDATING;
        
        String bestValueId = "bestValue_" + agentID + "-r" + roundNumber;
        
        ReconstructSecretProtocol.start(
            manager,
            bestValueId,
            prime,
            participants,
            shareStorage,
            this  // Listener
        );
    }
    
    @Override
    public void onReconstructComplete(String protocolId, String secretId, long reconstructedValue) {
        debug("Agent " + agentID + " onReconstructComplete: phase=" + currentPhase + ", value=" + reconstructedValue);
        if (currentPhase != Phase.UPDATING) {
            debug("Agent " + agentID + " IGNORING Reconstruct callback - wrong phase");
            return;
        }
        
        newValue = (int) reconstructedValue;
        
        // Round complete - notify listener
        // Synchronization with other agents is handled by the barrier at the agent level
        debug("Agent " + agentID + " completing round with newValue=" + newValue);
        completeRound();
    }
    
    /**
     * Marks the round as complete and notifies the listener.
     * Synchronization with other agents is handled by the BarrierProtocol at the agent level.
     */
    private void completeRound() {
        currentPhase = Phase.COMPLETE;
        complete = true;
        successful = true;
        
        debug("Agent " + agentID + " round " + roundNumber + " complete (newValue=" + newValue + ")");
        
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
        return "PDSA_ROUND";
    }
    
    @Override
    public Object getResult() {
        return newValue;
    }
    
    private String getRoundTag() {
        return "round-" + roundNumber;
    }
}
