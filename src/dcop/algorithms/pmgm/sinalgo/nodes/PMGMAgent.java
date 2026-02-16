package dcop.algorithms.pmgm.sinalgo.nodes;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dcop.algorithms.pmgm.IPMGMRoundListener;
import dcop.algorithms.pmgm.PMGMRoundProtocol;
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
 * PMGMAgent - Orchestrates P-MGM rounds using PMGMRoundProtocol.
 * 
 * Simple coordinator that:
 * - Creates PMGMRoundProtocol instances per round
 * - Handles round completion callbacks
 * - Routes messages to DistributedProtocolManager
 * 
 * Unlike PDSAAgent, PMGMAgent does not use a stochastic parameter.
 */
public class PMGMAgent extends Node implements IDCOPAgent, IPMGMRoundListener, IBarrierListener {
    
    // Agent state
    private int selectedValue;
    private int domainSize;
    private long algorithmSeed;
    private boolean isActive;
    
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
    private PMGMRoundProtocol currentRound;
    private BarrierProtocol currentBarrier;
    private int roundNumber;
    private int maxRounds = -1;  // -1 = unlimited, 0 = no rounds, >0 = limit
    
    // Cached objects (avoid repeated allocation in hot path)
    private List<Integer> cachedParticipants;
    private Map<String, Object> cachedResources;
    
    public PMGMAgent() {
        this.selectedValue = 0;
        this.domainSize = 5;
        this.algorithmSeed = 0;
        this.isActive = false;
        this.constraintMatrices = new HashMap<>();
        this.shareStorage = new ShareStorageManager();
        this.roundNumber = 0;
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
        cachedParticipants = null;
        cachedResources = null;
        
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
        
        // Register PMGM round protocol (cascades to all MPC dependencies)
        PMGMRoundProtocol.registerFactory(protocolManager);
        BarrierProtocol.registerFactory(protocolManager);
        
        // Set up local message callback for self-messages (avoids network round-trip)
        transport.setLocalMessageCallback((msg, senderId) -> {
            protocolManager.handleIncomingMessage(msg, senderId, getResourcesCached());
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
        if (currentRound == null && currentBarrier == null) {
            startNewRound();
        }
    }
    
    /**
     * Returns the cached list of all participants (all agents in the PMGM protocol).
     * Built once from constraintMatrices keys (neighbors) plus self, then cached.
     * 
     * @return Sorted list of all participant agent IDs (cached, do not modify)
     */
    private List<Integer> getParticipantsCached() {
        if (cachedParticipants == null) {
            List<Integer> participants = new ArrayList<>(constraintMatrices.keySet());
            participants.add(this.ID);
            Collections.sort(participants);
            cachedParticipants = Collections.unmodifiableList(participants);
        }
        return cachedParticipants;
    }
    
    /**
     * Returns the cached resources map needed for protocol message handling.
     * Contains only infrastructure dependencies; domain-specific data
     * is passed via constructor injection to the round protocols.
     * Built once and cached since contents don't change during a run.
     * 
     * @return Map of resources for protocol handling (cached, do not modify)
     */
    private Map<String, Object> getResourcesCached() {
        if (cachedResources == null) {
            cachedResources = new HashMap<>();
            cachedResources.put("shareStorage", shareStorage);
        }
        return cachedResources;
    }
    
    private void startNewRound() {
        List<Integer> participants = getParticipantsCached();
        
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
        currentRound = new PMGMRoundProtocol(
            this.ID,
            selectedValue,
            constraintMatrices,
            algorithmRandom,
            cryptoRandom,
            prime,
            roundNumber,
            this  // Listener
        );
        
        // Each agent has a unique protocol ID for their round instance.
        // Format: pmgm-round-{round}-agent{agentId}
        String myProtocolId = "pmgm-round-" + roundNumber + "-agent" + this.ID;
        
        Map<String, Object> params = new HashMap<>();
        params.put("protocolId", myProtocolId);
        params.put("shareStorage", shareStorage);
        params.put("roundNumber", roundNumber);
        
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
                
                protocolManager.handleIncomingMessage(protocolMsg, senderId, getResourcesCached());
            }
        }
    }
    
    @Override
    public void neighborhoodChange() {
        // Not needed for PMGM
    }
    
    @Override
    public void checkRequirements() throws sinalgo.configuration.WrongConfigurationException {
        // No specific requirements
    }
    
    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // Draw the node circle in MAGENTA (to distinguish from PDSA's blue and DSA's green)
        String text = String.valueOf(this.ID);
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 12, Color.MAGENTA);
    }
    
    @Override
    public String toString() {
        return "PMGMAgent " + ID + " (value=" + selectedValue + ", round=" + roundNumber + ")";
    }
}


