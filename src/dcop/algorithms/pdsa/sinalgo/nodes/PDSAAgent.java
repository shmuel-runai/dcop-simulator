package dcop.algorithms.pdsa.sinalgo.nodes;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dcop.algorithms.pdsa.IPDSARoundListener;
import dcop.algorithms.pdsa.PDSARoundProtocol;
import utils.protocols.common.barrier.BarrierProtocol;
import utils.protocols.common.barrier.IBarrierListener;
import dcop.common.nodes.IDCOPAgent;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import utils.protocols.adapters.sinalgo.SinalgoProtocolMessageWrapper;
import utils.protocols.core.DistributedProtocolManager;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.ShareStorageManager;
import utils.protocols.adapters.sinalgo.SinalgoMessageTransport;
import utils.protocols.core.IProtocolMessage;

/**
 * PDSAAgent - Orchestrates P-DSA rounds using PDSARoundProtocol.
 * 
 * Simple coordinator that:
 * - Creates PDSARoundProtocol instances per round
 * - Handles round completion callbacks
 * - Routes messages to DistributedProtocolManager
 */
public class PDSAAgent extends Node implements IDCOPAgent, IPDSARoundListener, IBarrierListener {
    
    // Agent state
    private int selectedValue;
    private int domainSize;
    private long algorithmSeed;
    private boolean isActive;
    private double stochastic;
    
    // Privacy: local constraints
    private Map<Integer, int[][]> constraintMatrices;
    
    // MPC infrastructure
    private long prime;
    private IShareStorage shareStorage;
    
    // Dual random generators
    private Random algorithmRandom;
    private Random cryptoRandom;
    
    // Protocol management
    private DistributedProtocolManager protocolManager;
    private SinalgoMessageTransport transport;
    
    // Round management
    private PDSARoundProtocol currentRound;
    private BarrierProtocol currentBarrier;
    private int roundNumber;
    private int maxRounds = -1;  // -1 = unlimited, 0 = no rounds, >0 = limit
    
    public PDSAAgent() {
        this.selectedValue = 0;
        this.domainSize = 5;
        this.algorithmSeed = 0;
        this.isActive = false;
        this.stochastic = 0.8;
        this.constraintMatrices = new HashMap<>();
        this.shareStorage = new ShareStorageManager();
        this.roundNumber = 0;
    }
    
    /**
     * Sets the stochastic parameter for PDSA.
     * This is the probability of changing when a better value is found.
     * 
     * @param stochastic Probability (0.0 to 1.0)
     */
    public void setStochastic(double stochastic) {
        this.stochastic = stochastic;
    }
    
    /**
     * Sets the local constraint matrices for this agent.
     * Each agent only knows constraints with its neighbors (privacy constraint).
     * 
     * @param constraints Map from neighbor ID to M x M cost matrix
     */
    public void setConstraintMatrices(Map<Integer, int[][]> constraints) {
        this.constraintMatrices = constraints;
    }
    
    /**
     * Sets the prime modulus for MPC operations.
     * 
     * @param prime The prime modulus (2^31 - 1)
     */
    public void setPrime(long prime) {
        this.prime = prime;
    }
    
    /**
     * Gets the share storage for MPC protocol access.
     * 
     * @return The share storage
     */
    public IShareStorage getShareStorage() {
        return shareStorage;
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
        this.roundNumber = 0;
        
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
        
        // CLEANUP: Clear all resources to free memory between iterations
        cleanup();
    }
    
    /**
     * Cleans up all resources to free memory.
     * Called when stopping the algorithm (between iterations/DCOP problems).
     * This is an aggressive cleanup to prevent memory accumulation.
     */
    private void cleanup() {
        // Debug: log cleanup stats for first agent only
        int shareCount = (shareStorage != null) ? shareStorage.getShareCount() : 0;
        int protocolCount = (protocolManager != null) ? protocolManager.getActiveProtocolCount() : 0;
        if (ID == 1) {
            System.out.println("[CLEANUP] Agent 1: " + shareCount + " shares, " + protocolCount + " protocols before cleanup");
        }
        
        // Clear current round protocol
        currentRound = null;
        
        // Clear barrier reference
        currentBarrier = null;
        
        // Clear ALL protocols from manager (including incomplete ones)
        if (protocolManager != null) {
            protocolManager.clearAllProtocols();
        }
        
        // Clear ALL shares from storage (including sticky)
        if (shareStorage != null) {
            shareStorage.clearAll();
        }
        
        // Clear local callback in transport to break reference cycle
        // (callback lambda captures 'this' and 'protocolManager')
        if (transport != null) {
            transport.setLocalMessageCallback(null);
        }
        
        // Null out all major object references to help GC
        // These objects may have complex internal state that forms reference cycles
        transport = null;
        protocolManager = null;
        shareStorage = null;
        constraintMatrices = null;
        algorithmRandom = null;
        cryptoRandom = null;
        
        // Force garbage collection after aggressive cleanup
        if (ID == 1) {
            System.gc();
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            System.out.println("[CLEANUP] Memory after GC: " + usedMB + " MB used");
        }
        
        // Note: roundNumber is NOT reset here - it's needed for reporting
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
            return roundNumber;
        }
        return -1;  // Unsupported property
    }
    
    // Sinalgo Node abstract methods
    
    @Override
    public void init() {
        // Initialize dual random generators
        this.algorithmRandom = new Random(algorithmSeed + this.ID);
        this.cryptoRandom = new Random(algorithmSeed + this.ID + 1000);
        
        // Initial value using algorithmRandom
        this.selectedValue = algorithmRandom.nextInt(domainSize);
        this.isActive = false;
        
        // Initialize MPC infrastructure
        this.transport = new SinalgoMessageTransport(this);
        this.protocolManager = new DistributedProtocolManager(transport, this.ID);
        
        // Register PDSA round protocol
        PDSARoundProtocol.registerFactory(protocolManager);
        BarrierProtocol.registerFactory(protocolManager);
        
        // Set up local message callback for self-messages (avoids network round-trip)
        transport.setLocalMessageCallback((msg, senderId) -> {
            protocolManager.handleIncomingMessage(msg, senderId, buildResources());
        });
    }
    
    @Override
    public void preStep() {
        if (!isActive) return;
        
        // Check if we've reached max rounds
        if (maxRounds >= 0 && roundNumber >= maxRounds) {
            this.isActive = false;
            return;
        }
        
        // Start new round only when:
        // 1. No round is active (currentRound == null)
        // 2. No barrier is pending (currentBarrier == null)
        // After a round completes, we signal the barrier but don't start new round yet.
        // Only after the barrier completes (all agents done) do we start the next round.
        if (currentRound == null && currentBarrier == null) {
            startNewRound();
        }
    }
    
    /**
     * Builds the list of all participants (all agents in the PDSA protocol).
     * Derived from constraintMatrices keys (neighbors) plus self.
     * 
     * @return Sorted list of all participant agent IDs
     */
    private List<Integer> buildParticipants() {
        List<Integer> participants = new ArrayList<>(constraintMatrices.keySet());
        participants.add(this.ID);
        Collections.sort(participants);
        return participants;
    }
    
    /**
     * Builds the resources map needed for protocol message handling.
     * Contains only infrastructure dependencies; domain-specific data
     * is passed via constructor injection to the round protocols.
     * 
     * @return Map of resources for protocol handling
     */
    private Map<String, Object> buildResources() {
        Map<String, Object> resources = new HashMap<>();
        resources.put("shareStorage", shareStorage);
        // Note: Domain-specific params (constraintMatrices, algorithmRandom, cryptoRandom,
        // stochastic, selectedValue) are passed via constructor to round protocols
        // and then forwarded to sub-protocols via their params maps.
        return resources;
    }
    
    private void startNewRound() {
        List<Integer> participants = buildParticipants();
        
        // Create barrier for this round's synchronization
        currentBarrier = new BarrierProtocol();
        String barrierId = BarrierProtocol.computeProtocolId("round-" + roundNumber);
        
        Map<String, Object> barrierParams = new HashMap<>();
        barrierParams.put("protocolId", barrierId);
        barrierParams.put("participants", participants);
        // NOTE: "agentID" removed - manager provides "agentId"
        barrierParams.put("listener", this);
        
        protocolManager.startProtocol(currentBarrier, barrierParams, participants);
        
        // Create round protocol
        currentRound = new PDSARoundProtocol(
            this.ID,
            selectedValue,
            constraintMatrices,
            stochastic,
            algorithmRandom,
            cryptoRandom,
            prime,
            roundNumber,
            this  // Listener
        );
        
        // Each agent has a unique protocol ID for their round instance.
        // Format: pdsa-round-{round}-agent{agentId}
        String myProtocolId = "pdsa-round-" + roundNumber + "-agent" + this.ID;
        
        Map<String, Object> params = new HashMap<>();
        params.put("protocolId", myProtocolId);
        params.put("shareStorage", shareStorage);
        params.put("roundNumber", roundNumber);
        
        // Use startProtocol with participants derived from constraintMatrices
        protocolManager.startProtocol(currentRound, params, participants);
    }
    
    @Override
    public void onRoundComplete(String protocolId, int roundNumber, int newValue) {
        // Update value
        this.selectedValue = newValue;
        
        // Cleanup round protocol
        protocolManager.removeProtocol(protocolId);
        currentRound = null;
        
        // Signal the barrier - next round starts when ALL agents are done
        if (currentBarrier != null) {
            currentBarrier.signal();
        }
    }
    
    @Override
    public void onBarrierComplete(String barrierId) {
        // Cleanup barrier protocol
        protocolManager.removeProtocol(barrierId);
        currentBarrier = null;
        
        // MEMORY CLEANUP: Aggressive cleanup to prevent memory leak
        // Clear ALL non-sticky shares (keeps only pre-distributed secrets like r-key)
        int sharesCleared = shareStorage.clearNonSticky();
        
        // Also clear ALL protocols from manager (sub-protocols hold references)
        int protocolsCleared = protocolManager.clearAllProtocols();
        
        // Hint to GC that now is a good time to reclaim memory
        // This is especially important after clearing thousands of protocol instances
        if (ID == 1) {
            // Only one agent triggers GC to avoid redundant calls
            System.gc();
        }
        
        // Increment round - all agents have completed
        this.roundNumber++;
        
        // Next round will start in next preStep
    }
    
    @Override
    public void postStep() {
        // Protocol handles everything
    }
    
    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            
            if (msg instanceof SinalgoProtocolMessageWrapper) {
                SinalgoProtocolMessageWrapper wrapper = (SinalgoProtocolMessageWrapper) msg;
                IProtocolMessage protocolMsg = wrapper.unwrap();
                int senderId = inbox.getSender().ID;
                
                protocolManager.handleIncomingMessage(protocolMsg, senderId, buildResources());
            }
        }
    }
    
    @Override
    public void neighborhoodChange() {
        // Not needed for PDSA
    }
    
    @Override
    public void checkRequirements() throws sinalgo.configuration.WrongConfigurationException {
        // No specific requirements
    }
    
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // Draw the node circle in BLUE (to distinguish from DSA's green)
        String text = String.valueOf(this.ID);
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 12, Color.BLUE);
    }
    
    @Override
    public String toString() {
        return "PDSAAgent " + ID + " (value=" + selectedValue + ", round=" + roundNumber + ")";
    }
}
