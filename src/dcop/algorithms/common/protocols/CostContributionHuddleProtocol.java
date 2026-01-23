package dcop.algorithms.common.protocols;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.core.IProtocolMessage;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;
import utils.crypto.secretsharing.ShareGenerator;
import dcop.algorithms.common.protocols.messages.VectorCostContributionMessage;
import dcop.algorithms.common.protocols.messages.ReadyToAssistMessage;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

/**
 * CostContributionHuddleProtocol - A "huddle" where all agents exchange cost contributions.
 * 
 * This is a shared protocol used by privacy-preserving DCOP algorithms (PDSA, PMGM, etc.).
 * The name "Huddle" reflects that this protocol does multiple things:
 * - Everyone helps everyone else by sharing their cost contributions
 * - Everyone waits for others to signal they're ready to assist
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * <h3>Shared Protocol ID (like Barrier)</h3>
 * All agents use the SAME protocol ID for a given round: "huddle-round-{N}".
 * This simplifies message routing - no need to compute per-agent protocol IDs.
 * Messages contain targetAgent fields to distinguish who's being helped.
 * 
 * Each agent runs ONE instance of this protocol per round. It handles:
 * 1. Sending cost contribution shares for all OTHER agents (N-1 targets)
 * 2. Accumulating shares for all targets from all senders
 * 3. Sending "ready" to each target when their shares are accumulated
 * 4. Waiting for "ready" from all other agents (for MY target)
 * 5. Notifying listener when MY collection is complete
 * 
 * Protocol ID format: huddle-round-{roundNumber}
 */
public class CostContributionHuddleProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "COST_HUDDLE";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Computes the shared protocol ID for all agents in a given round.
     * All agents use the SAME protocol ID so messages route to the shared huddle.
     * 
     * @param roundNumber The current round number
     * @return The shared huddle protocol ID
     */
    public static String computeProtocolId(int roundNumber) {
        return "huddle-round-" + roundNumber;
    }
    
    /**
     * Registers the factory for this protocol type.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Both initiator and responder can be created via factory
        // (each agent creates their own instance)
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            CostContributionHuddleProtocol::new, 
            Responder::new);
    }
    
    // =========================================================================
    // INITIATOR STATE
    // =========================================================================
    
    // Debug logging
    private static final boolean DEBUG = false;
    private static final int DEBUG_AGENT_ID = 7; // -1 = all agents
    
    private void debug(String message) {
        if (DEBUG && (DEBUG_AGENT_ID == -1 || agentID == DEBUG_AGENT_ID)) {
            System.out.println(message);
        }
    }
    
    private void panic(String message) {
        throw new RuntimeException("CostHuddle FATAL [Agent " + agentID + "]: " + message);
    }
    
    // Protocol identity
    private String protocolId;
    private int roundNumber;
    
    // Agent context (set via constructor - proper injection)
    private final int agentID;
    private final int initialValue;
    private final Map<Integer, int[][]> constraintMatrices;
    private final Random cryptoRandom;
    private final long prime;
    private final ICostContributionHuddleListener listener;
    
    // Protocol infrastructure (set during initialize)
    private IMessageTransport transport;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    
    // Embedded responder for self-message handling (uniform flow)
    private Responder selfResponder;
    
    // State tracking
    private int readyCount;                  // Ready messages received for MY target
    private Set<Integer> readyReceivedFrom;  // Track which agents sent ready (for duplicate detection)
    
    // Completion state
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /**
     * Default constructor for factory pattern (receiving-side only).
     */
    public CostContributionHuddleProtocol() {
        this.agentID = -1;
        this.initialValue = -1;
        this.constraintMatrices = null;
        this.cryptoRandom = null;
        this.prime = 0;
        this.listener = null;
        this.readyCount = 0;
        this.readyReceivedFrom = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    /**
     * Constructor with domain parameters (proper injection for initiator).
     */
    public CostContributionHuddleProtocol(
            int agentID, int initialValue,
            Map<Integer, int[][]> constraintMatrices,
            Random cryptoRandom, long prime,
            int roundNumber,
            IShareStorage shareStorage,
            ICostContributionHuddleListener listener) {
        this.agentID = agentID;
        this.initialValue = initialValue;
        this.constraintMatrices = constraintMatrices;
        this.cryptoRandom = cryptoRandom;
        this.prime = prime;
        this.roundNumber = roundNumber;
        this.shareStorage = shareStorage;
        this.listener = listener;
        this.readyCount = 0;
        this.readyReceivedFrom = new HashSet<>();
        this.complete = false;
        this.successful = false;
    }
    
    // =========================================================================
    // INITIATOR INITIALIZATION
    // =========================================================================
    
    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.transport = (IMessageTransport) params.get("transport");
        this.participants = (List<Integer>) params.get("participants");
        
        // shareStorage: prefer constructor-injected value, fall back to params for responder
        if (this.shareStorage == null) {
            this.shareStorage = (IShareStorage) params.get("shareStorage");
        }
        
        // Create embedded responder for self-message handling (uniform flow)
        this.selfResponder = new Responder();
        Map<String, Object> responderParams = new HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentID > 0 ? agentID : params.get("agentId"));
        responderParams.put("transport", transport);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("participants", participants);
        responderParams.put("roundNumber", roundNumber);
        responderParams.put("prime", prime);
        this.selfResponder.initialize(responderParams);
        
        debug("CostHuddle initialized: agent=" + agentID + ", round=" + roundNumber + 
              ", participants=" + participants.size());
        
        // Send cost contributions for ALL other targets (only if we have domain data)
        if (constraintMatrices != null && !constraintMatrices.isEmpty() && cryptoRandom != null) {
            sendAllCostContributions();
        } else {
            debug("CostHuddle: Receiving-side instance (no domain data), skipping send");
        }
    }
    
    /**
     * Sends cost contribution shares for all other target agents.
     */
    private void sendAllCostContributions() {
        for (int targetAgent : participants) {
            if (targetAgent == agentID) continue;  // Don't send shares for myself
            sendCostContributionsForTarget(targetAgent);
        }
        
        debug("Agent " + agentID + " sent cost contributions for " + 
              (participants.size() - 1) + " targets");
    }
    
    /**
     * Sends cost contribution shares for a single target agent to all participants.
     * 
     * <h3>What</h3>
     * For each possible value the target agent could choose (domain values 0..M-1),
     * this method creates secret shares of MY cost contribution and sends them to
     * ALL participants.
     * 
     * <h3>Why</h3>
     * In privacy-preserving DCOP, each agent needs to evaluate costs for different
     * value choices, but WITHOUT revealing actual cost values. We use Shamir secret
     * sharing: split each cost into N shares such that any T shares can reconstruct
     * the value, but fewer than T shares reveal nothing.
     * 
     * Example: If I'm agent 2 with value=1, and target is agent 5:
     * - I look up my constraint matrix with agent 5: C_{2,5}
     * - Row 1 (my current value) gives costs for each of agent 5's possible values
     * - I secret-share each of these costs and distribute to all agents
     * 
     * <h3>How</h3>
     * 1. Get my row from the constraint matrix (indexed by my current value)
     * 2. For each column j (target's domain value):
     *    - Generate N shares of cost[j] using Shamir secret sharing
     *    - Assign share i to participant i
     * 3. Bundle all shares for each recipient into one message
     * 4. Send to each recipient's huddle protocol (using THEIR protocol ID)
     * 
     * The result: Each participant now holds one share of each cost value.
     * Later, participants sum their shares locally (Shamir is linear), giving
     * shares of the total cost vector Wb[j] = sum of all agents' contributions.
     * 
     * @param targetAgent The agent whose cost vector we're contributing to
     */
    private void sendCostContributionsForTarget(int targetAgent) {
        // Step 1: Get my cost row from constraint matrix with target
        // matrix[myValue][targetValue] = cost when I choose myValue, target chooses targetValue
        int[][] matrix = constraintMatrices.get(targetAgent);
        if (matrix == null) {
            panic("No constraint matrix with target agent " + targetAgent);
            return;
        }
        
        int[] myRow = matrix[initialValue];  // My costs for each of target's possible values
        int domainSize = myRow.length;
        int threshold = participants.size() / 2;  // T = N/2 for (T,N) threshold scheme
        String baseSecretID = "Wb_" + targetAgent + "-r" + roundNumber;
        
        // Step 2: Prepare share buckets for each recipient
        // Each recipient will get one share per domain value
        Map<Integer, List<Share>> sharesPerRecipient = new HashMap<>();
        for (int recipientId : participants) {
            sharesPerRecipient.put(recipientId, new ArrayList<>());
        }
        
        // Step 3: Generate shares for each cost value (one per domain column)
        // For domain value j: split cost myRow[j] into N shares
        for (int column = 0; column < domainSize; column++) {
            long value = myRow[column];
            ShareGenerator gen = new ShareGenerator(value, threshold, prime, cryptoRandom);
            
            // Generate share for each participant and add to their bucket
            for (int recipientId : participants) {
                Share share = gen.generateShare(recipientId);
                sharesPerRecipient.get(recipientId).add(share);
            }
        }
        
        // Step 4: Send bundled shares to each recipient
        // All agents use the SAME shared protocol ID (like Barrier)
        for (int recipientId : participants) {
            VectorCostContributionMessage msg = new VectorCostContributionMessage(
                protocolId, roundNumber, targetAgent, baseSecretID,
                sharesPerRecipient.get(recipientId)
            );
            transport.sendMessage(msg, recipientId);
        }
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof VectorCostContributionMessage) {
            // Route to selfResponder for uniform handling
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof ReadyToAssistMessage) {
            handleReady((ReadyToAssistMessage) msg, senderId);
        }
    }
    
    private void handleReady(ReadyToAssistMessage msg, int senderId) {
        if (msg.getRoundNumber() != roundNumber) return;
        
        // Only process ready messages for MY target (this agent)
        if (msg.getTargetAgent() != agentID) return;
        
        // Already complete - ignore
        if (complete) return;
        
        // Check for duplicates
        if (readyReceivedFrom.contains(senderId)) {
            debug("Agent " + agentID + " ignoring duplicate ready from " + senderId);
            return;
        }
        readyReceivedFrom.add(senderId);
        
        readyCount++;
        
        // Expected: N ready messages (from all participants including self)
        int expected = participants.size();
        
        debug("Agent " + agentID + " received ready from " + senderId + 
              " (" + readyCount + "/" + expected + ")");
        
        if (readyCount < expected) {
            return;
        }
        
        complete = true;
        successful = true;
        
        debug("CostHuddle COMPLETE for agent " + agentID);
        
        if (listener != null) {
            listener.onHuddleComplete(protocolId, agentID, roundNumber);
        }
    }
    
    @Override
    public boolean isComplete() { return complete; }
    
    @Override
    public boolean isSuccessful() { return successful; }
    
    @Override
    public String getProtocolId() { return protocolId; }
    
    @Override
    public String getProtocolType() { return PROTOCOL_TYPE; }
    
    @Override
    public Object getResult() { return null; }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles VectorCostContributionMessage and accumulates shares.
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private List<Integer> participants;
        private int roundNumber;
        private long prime;
        
        // State tracking - per target
        private Map<Integer, Integer> contributionCount;  // targetAgent -> shares received
        private Set<Integer> readySentTo;                 // Targets we've sent ready to
        
        private boolean complete;
        private boolean successful;
        
        public Responder() {
            this.contributionCount = new HashMap<>();
            this.readySentTo = new HashSet<>();
            this.complete = false;
            this.successful = false;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void initialize(Map<String, Object> params) {
            this.protocolId = (String) params.get("protocolId");
            this.agentId = (Integer) params.get("agentId");
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
            this.participants = (List<Integer>) params.get("participants");
            this.roundNumber = params.get("roundNumber") != null ? (Integer) params.get("roundNumber") : 0;
            this.prime = params.get("prime") != null ? (Long) params.get("prime") : 0L;
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof VectorCostContributionMessage) {
                handleCostContribution((VectorCostContributionMessage) msg, senderId);
            }
        }
        
        private void handleCostContribution(VectorCostContributionMessage msg, int senderId) {
            if (msg.getRoundNumber() != roundNumber) {
                return;
            }
            
            int targetAgent = msg.getTargetAgent();
            List<Share> shares = msg.getShares();
            String baseSecretID = msg.getBaseSecretID();
            
            // Accumulate shares
            for (int i = 0; i < shares.size(); i++) {
                String secretID = msg.getSecretID(i);
                Share incomingShare = shares.get(i);
                
                Share existing = shareStorage.getShare(secretID);
                
                if (existing != null) {
                    long storeSecret = existing.getSecret() + incomingShare.getSecret();
                    long storeVal = (existing.getValue() + incomingShare.getValue()) % prime;
                    Share updated = new Share(storeSecret, existing.getIndex(), storeVal);
                    shareStorage.storeShare(secretID, updated, "cost-huddle");
                } else {
                    shareStorage.storeShare(secretID, incomingShare, "cost-huddle");
                }
            }
            
            // Check if ready for this target
            checkContributionsComplete(targetAgent, senderId);
        }
        
        private void checkContributionsComplete(int targetAgent, int senderId) {
            // Track message count for this target (one message per sender)
            int count = contributionCount.getOrDefault(targetAgent, 0) + 1;
            contributionCount.put(targetAgent, count);
            
            // Expected: N-1 messages (from all agents except target)
            int expected = participants.size() - 1;
            
            if (readySentTo.contains(targetAgent)) {
                return;
            }
            
            if (count < expected) {
                return;
            }
            
            // Send ready message to target using shared protocol ID
            // targetAgent field tells the recipient this ready is for them
            ReadyToAssistMessage ready = new ReadyToAssistMessage(protocolId, roundNumber, targetAgent);
            transport.sendMessage(ready, targetAgent);
            readySentTo.add(targetAgent);
        }
        
        @Override
        public boolean isComplete() { return complete; }
        
        @Override
        public boolean isSuccessful() { return successful; }
        
        @Override
        public String getProtocolId() { return protocolId; }
        
        @Override
        public String getProtocolType() { return PROTOCOL_TYPE + "_RESPONDER"; }
        
        @Override
        public Object getResult() { return null; }
    }
}

