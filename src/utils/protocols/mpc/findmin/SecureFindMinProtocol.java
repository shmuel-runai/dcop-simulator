package utils.protocols.mpc.findmin;

import utils.protocols.core.*;
import utils.protocols.mpc.securecompare.ISecureCompareListener;
import utils.protocols.mpc.securesub.ISecureSubListener;
import utils.protocols.mpc.secureknownsub.ISecureKnownSubListener;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.secureadd.ISecureAddListener;
import utils.protocols.mpc.secureadd.SecureAddProtocol;
import utils.protocols.mpc.securesub.SecureSubProtocol;
import utils.protocols.mpc.securecompare.SecureCompareProtocol;
import utils.protocols.mpc.secureknownsub.SecureKnownSubProtocol;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.crypto.secretsharing.IShareStorage;
import utils.crypto.secretsharing.Share;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Secure FindMin Meta-Protocol.
 * 
 * Finds the minimum value and its index in an array of shared secrets using
 * a linear scan with difference-based conditional updates.
 * 
 * This class handles the INITIATOR side of the protocol.
 * The inner class {@link Responder} handles the RESPONDER side.
 * 
 * Algorithm (per iteration):
 *   Wave 1: Compare(arr[i], v), SubValue(arr[i], v), KnownSub(i, k) - all parallel
 *   Wave 2: Multiply(beta, value_diff), Multiply(beta, index_diff) - both parallel  
 *   Wave 3: Add(v, gamma), Add(k, delta) - both parallel
 * 
 * Performance: ~3 operation times per iteration with full parallelism.
 */
public class SecureFindMinProtocol implements IDistributedProtocol,
        ISecureCompareListener, ISecureSubListener, ISecureKnownSubListener,
        ISecureMultiplyListener, ISecureAddListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_FIND_MIN";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure FindMin protocol.
     * 
     * @param storageTag Tag for intermediate/result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String baseArrayId, String valueOutputId, String indexOutputId,
                               int firstIndex, int lastIndex, String rSecretId,
                               long prime, int threshold,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               ISecureFindMinListener listener) {
        SecureFindMinProtocol protocol = new SecureFindMinProtocol(
            baseArrayId, valueOutputId, indexOutputId,
            firstIndex, lastIndex, rSecretId,
            prime, threshold, shareStorage, storageTag, listener
        );
        Map<String, Object> params = new HashMap<>();
        params.put("manager", manager);
        return manager.startProtocol(protocol, params, participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first
        SecureCompareProtocol.registerFactory(manager);
        SecureSubProtocol.registerFactory(manager);
        SecureKnownSubProtocol.registerFactory(manager);
        SecureMultiplyProtocol.registerFactory(manager);
        SecureAddProtocol.registerFactory(manager);
        
        // Initiator uses constructor injection, so null
        manager.registerProtocolFactory(PROTOCOL_TYPE, null, Responder::new);
    }
    
    // =========================================================================
    // INITIATOR STATE
    // =========================================================================
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println(msg); }
    
    // Protocol identity
    private String protocolId;
    private int agentId;
    
    // Infrastructure (set during initialize)
    private IMessageTransport transport;
    private DistributedProtocolManager manager;
    private List<Integer> participants;
    
    // Domain data (set via constructor - proper injection)
    private final String baseArrayId;
    private final String vId;          // Output: min value share ID
    private final String kId;          // Output: min index share ID
    private final int firstIndex;
    private final int lastIndex;
    private final String rSecretId;    // For SecureMultiply
    private final long prime;
    private final int threshold;
    private final IShareStorage shareStorage;
    private final String storageTag;
    private final ISecureFindMinListener listener;
    
    // Embedded responder for self-message handling (uniform flow)
    private Responder selfResponder;
    
    // Iteration state
    private int currentIteration;
    private Set<Integer> initConfirmations;
    
    // Protocol phases
    private enum FindMinPhase {
        INITIALIZING,
        WAVE1,      // Compare, SubValue, KnownSub in parallel
        WAVE2,      // Both multiplies in parallel
        WAVE3,      // Both adds in parallel
        COMPLETE
    }
    
    private FindMinPhase currentPhase;
    
    // Temporary share IDs per iteration
    private String betaId;       // Comparison result
    private String valueDiffId;  // array[i] - v
    private String indexDiffId;  // i - k
    private String gammaId;      // beta * value_diff
    private String deltaId;      // beta * index_diff
    
    // Wave completion tracking
    private Set<String> completedOperations;
    
    // State
    private boolean complete;
    private boolean successful;
    
    // =========================================================================
    // INITIATOR CONSTRUCTOR (proper injection)
    // =========================================================================
    
    public SecureFindMinProtocol(
            String baseArrayId, String valueOutputId, String indexOutputId,
            int firstIndex, int lastIndex, String rSecretId,
            long prime, int threshold,
            IShareStorage shareStorage, String storageTag,
            ISecureFindMinListener listener) {
        this.baseArrayId = baseArrayId;
        this.vId = valueOutputId;
        this.kId = indexOutputId;
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.rSecretId = rSecretId;
        this.prime = prime;
        this.threshold = threshold;
        this.shareStorage = shareStorage;
        this.storageTag = storageTag;
        this.listener = listener;
        this.initConfirmations = new HashSet<>();
        this.completedOperations = new HashSet<>();
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
        this.agentId = (Integer) params.get("agentId");
        this.transport = (IMessageTransport) params.get("transport");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.participants = (List<Integer>) params.get("participants");
        
        // Create embedded responder for self-message handling (uniform flow)
        this.selfResponder = new Responder();
        Map<String, Object> responderParams = new HashMap<>();
        responderParams.put("protocolId", protocolId);
        responderParams.put("agentId", agentId);
        responderParams.put("shareStorage", shareStorage);
        responderParams.put("storageTag", storageTag);
        responderParams.put("transport", transport);
        this.selfResponder.initialize(responderParams);
        
        debug("FindMin [" + protocolId + "]: Starting on array " + baseArrayId + 
              "[" + firstIndex + ".." + lastIndex + "]");
        
        startInitialization();
    }
    
    private void startInitialization() {
        currentPhase = FindMinPhase.INITIALIZING;
        initConfirmations.clear();
        
        // Broadcast init message to all participants (including self via multicast)
        FindMinInitMessage msg = new FindMinInitMessage(
            protocolId, baseArrayId, firstIndex, lastIndex, vId, kId, 
            prime, threshold, rSecretId, storageTag
        );
        transport.multicast(msg, participants);
    }
    
    // =========================================================================
    // INITIATOR MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof FindMinInitMessage) {
            // Self-message: route to embedded responder (uniform flow)
            selfResponder.handleMessage(msg, senderId);
        } else if (msg instanceof FindMinCompleteMessage) {
            handleInitConfirmation(senderId);
        }
    }
    
    private void handleInitConfirmation(int fromAgent) {
        initConfirmations.add(fromAgent);
        
        debug("FindMin [" + protocolId + "]: Got confirmation from " + fromAgent + 
              " (" + initConfirmations.size() + "/" + participants.size() + ")");
        
        if (initConfirmations.size() < participants.size()) {
            return;
        }
        
        // All agents ready, start first iteration
        debug("FindMin [" + protocolId + "]: All confirmations received, starting iterations");
        currentIteration = firstIndex + 1;
        if (currentIteration > lastIndex) {
            // Array of size 1, already have the min
            finalizeProtocol();
        } else {
            startIteration();
        }
    }
    
    private void startIteration() {
        if (currentIteration > lastIndex) {
            finalizeProtocol();
            return;
        }
        
        currentPhase = FindMinPhase.WAVE1;
        completedOperations.clear();
        
        // Generate temp share IDs for this iteration
        betaId = "findmin-" + protocolId + "-i" + currentIteration + "-beta";
        valueDiffId = "findmin-" + protocolId + "-i" + currentIteration + "-vdiff";
        indexDiffId = "findmin-" + protocolId + "-i" + currentIteration + "-idiff";
        gammaId = "findmin-" + protocolId + "-i" + currentIteration + "-gamma";
        deltaId = "findmin-" + protocolId + "-i" + currentIteration + "-delta";
        
        String currentElement = baseArrayId + "[" + currentIteration + "]";
        
        // WAVE 1: Start all three operations in parallel
        
        // 3.1: beta = SecureCompare(array[i], v)
        // Note: Uses pre-distributed "r-key" and its bits for LSB extraction
        SecureCompareProtocol.start(
            manager, currentElement, vId, betaId, "r-key", "r-key",
            prime, threshold, participants, shareStorage, storageTag, this
        );
        
        // 3.2: value_diff = SecureSub(array[i], v)
        SecureSubProtocol.start(
            manager, currentElement, vId, valueDiffId, prime,
            participants, shareStorage, storageTag, this
        );
        
        // 3.3: index_diff = SecureKnownSub(i - k) - i is known!
        // knownIsLeft=true means: result = knownValue - secret = i - k
        SecureKnownSubProtocol.start(
            manager, (long)currentIteration, kId, indexDiffId, true, prime,
            participants, shareStorage, storageTag, this
        );
    }
    
    // WAVE 1 completion handlers
    
    @Override
    public void onSecureCompareComplete(String protocolId, String resultSecretId) {
        if (currentPhase != FindMinPhase.WAVE1) return;
        
        completedOperations.add("compare");
        checkWave1Complete();
    }
    
    @Override
    public void onSecureSubComplete(String protocolId, String resultSecretId) {
        if (currentPhase != FindMinPhase.WAVE1) return;
        
        completedOperations.add("sub-value");
        checkWave1Complete();
    }
    
    @Override
    public void onSecureKnownSubComplete(String protocolId, String resultSecretId) {
        if (currentPhase != FindMinPhase.WAVE1) return;
        
        completedOperations.add("known-sub");
        checkWave1Complete();
    }
    
    private void checkWave1Complete() {
        if (completedOperations.size() < 3) {
            return;
        }
        startWave2();
    }
    
    private void startWave2() {
        currentPhase = FindMinPhase.WAVE2;
        completedOperations.clear();
        
        // WAVE 2: Both multiplications in parallel
        
        // 3.4: gamma = SecureMultiply(beta, value_diff)
        SecureMultiplyProtocol.start(
            manager, betaId, valueDiffId, gammaId, rSecretId, prime,
            participants, shareStorage, storageTag, this
        );
        
        // 3.5: delta = SecureMultiply(beta, index_diff)
        SecureMultiplyProtocol.start(
            manager, betaId, indexDiffId, deltaId, rSecretId, prime,
            participants, shareStorage, storageTag, this
        );
    }
    
    // WAVE 2 completion handler
    
    @Override
    public void onSecureMultiplyComplete(String protocolId, String resultSecretId) {
        if (currentPhase != FindMinPhase.WAVE2) return;
        
        if (resultSecretId.equals(gammaId)) {
            completedOperations.add("gamma");
        } else if (resultSecretId.equals(deltaId)) {
            completedOperations.add("delta");
        }
        
        if (completedOperations.size() < 2) {
            return;
        }
        startWave3();
    }
    
    private void startWave3() {
        currentPhase = FindMinPhase.WAVE3;
        completedOperations.clear();
        
        // WAVE 3: Both additions in parallel
        // IMPORTANT: Store result directly to vId/kId (not intermediate IDs)
        // This ensures ALL agents have the updated shares, not just the initiator
        
        // 3.6: v_new = SecureAdd(v, gamma) -> store directly in vId
        SecureAddProtocol.start(
            manager, vId, gammaId, vId, prime,
            participants, shareStorage, storageTag, this
        );
        
        // 3.7: k_new = SecureAdd(k, delta) -> store directly in kId
        SecureAddProtocol.start(
            manager, kId, deltaId, kId, prime,
            participants, shareStorage, storageTag, this
        );
    }
    
    // WAVE 3 completion handler
    
    @Override
    public void onSecureAddComplete(String protocolId, String resultSecretId) {
        if (currentPhase != FindMinPhase.WAVE3) return;
        
        completedOperations.add(resultSecretId);
        
        if (completedOperations.size() < 2) {
            return;
        }
        
        // v and k are already updated on ALL agents (stored directly by SecureAdd)
        // Just proceed to next iteration
        currentIteration++;
        startIteration();
    }
    
    private void finalizeProtocol() {
        currentPhase = FindMinPhase.COMPLETE;
        complete = true;
        successful = true;
        
        debug("FindMin [" + protocolId + "]: Complete - min stored in " + vId + ", index in " + kId);
        
        // v and k already contain the final results
        if (listener != null) {
            listener.onSecureFindMinComplete(this.protocolId, vId, kId);
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
    public Object getResult() {
        return successful ? new String[]{vId, kId} : null;
    }
    
    // =========================================================================
    // INNER CLASS: RESPONDER
    // =========================================================================
    
    /**
     * Responder - handles FindMinInitMessage and sets up local shares.
     */
    public static class Responder implements IDistributedProtocol {
        
        private String protocolId;
        private int agentId;
        private IMessageTransport transport;
        private IShareStorage shareStorage;
        private String storageTag;
        
        private boolean complete;
        private boolean successful;
        private boolean initProcessed;
        
        public Responder() {
            this.complete = false;
            this.successful = false;
            this.initProcessed = false;
        }
        
        @Override
        public void initialize(Map<String, Object> params) {
            this.protocolId = (String) params.get("protocolId");
            this.agentId = (Integer) params.get("agentId");
            this.transport = (IMessageTransport) params.get("transport");
            this.shareStorage = (IShareStorage) params.get("shareStorage");
            this.storageTag = (String) params.get("storageTag");
        }
        
        @Override
        public void handleMessage(IProtocolMessage msg, int senderId) {
            if (msg instanceof FindMinInitMessage) {
                handleInitMessage((FindMinInitMessage) msg, senderId);
            }
        }
        
        private void handleInitMessage(FindMinInitMessage msg, int senderId) {
            // Prevent duplicate processing
            if (initProcessed) {
                return;
            }
            initProcessed = true;
            
            // Extract parameters from message
            String baseArrayId = msg.getBaseArrayId();
            int firstIndex = msg.getFirstIndex();
            String vId = msg.getVId();
            String kId = msg.getKId();
            
            // Override storageTag from message if we don't have one
            if (this.storageTag == null) {
                this.storageTag = msg.getStorageTag();
            }
            
            // Step 1: Copy array[first] to v
            String firstElement = baseArrayId + "[" + firstIndex + "]";
            Share firstShare = shareStorage.getShare(firstElement);
            if (firstShare == null) {
                throw new ProtocolException("Array element not found: " + firstElement);
            }
            storeShare(vId, firstShare);
            
            // Step 2: Create share for index 'first' locally (no distribution needed!)
            // Each agent creates share(first) = (first, agentId, first)
            Share kShare = new Share(firstIndex, agentId, firstIndex);
            storeShare(kId, kShare);
            
            // Send confirmation to initiator
            FindMinCompleteMessage ack = new FindMinCompleteMessage(protocolId);
            transport.sendMessage(ack, senderId);
            
            complete = true;
            successful = true;
        }
        
        private void storeShare(String key, Share share) {
            if (storageTag == null) {
                shareStorage.storeStickyShare(key, share);
            } else {
                shareStorage.storeShare(key, share, storageTag);
            }
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
