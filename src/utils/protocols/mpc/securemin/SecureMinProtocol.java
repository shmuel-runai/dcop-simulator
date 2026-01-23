package utils.protocols.mpc.securemin;

import utils.protocols.core.*;
import utils.protocols.mpc.secureadd.ISecureAddListener;
import utils.protocols.mpc.secureadd.SecureAddProtocol;
import utils.protocols.mpc.securesub.SecureSubProtocol;
import utils.protocols.mpc.securecompare.SecureCompareProtocol;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.protocols.mpc.securesub.ISecureSubListener;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securecompare.ISecureCompareListener;
import utils.crypto.secretsharing.IShareStorage;

import java.util.List;
import java.util.Map;

/**
 * Secure Min Meta-Protocol.
 * 
 * Computes min(left, right) by orchestrating existing MPC protocols.
 * This demonstrates advanced protocol composition with sequential dependencies.
 * 
 * Algorithm: result = right + (compare(left, right) × (left - right))
 * 
 * Proof:
 * - If left < right: result = right + (1 × (left - right)) = left ✓
 * - If left ≥ right: result = right + (0 × (left - right)) = right ✓
 * 
 * Protocol Flow:
 * Phase 1: SecureSub(left, right) → diff AND SecureCompare(left, right) → cmp (parallel)
 * Phase 2: SecureMultiply(cmp, diff) → scaled
 * Phase 3: SecureAdd(right, scaled) → result
 */
public class SecureMinProtocol implements IDistributedProtocol,
        ISecureSubListener, ISecureCompareListener, ISecureMultiplyListener, ISecureAddListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_MIN";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Min protocol.
     * 
     * @param storageTag Tag for result storage (null for sticky)
     */
    public static String start(DistributedProtocolManager manager,
                               String leftSecretId, String rightSecretId, String resultSecretId,
                               String rSecretId, long prime, int threshold,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               ISecureMinListener listener) {
        SecureMinProtocol protocol = new SecureMinProtocol();
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("leftSecretId", leftSecretId);
        params.put("rightSecretId", rightSecretId);
        params.put("resultSecretId", resultSecretId);
        params.put("rSecretId", rSecretId);
        params.put("prime", prime);
        params.put("threshold", threshold);
        params.put("shareStorage", shareStorage);
        params.put("storageTag", storageTag);
        params.put("listener", listener);
        params.put("manager", manager);
        return manager.startProtocol(protocol, params, participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies first
        SecureSubProtocol.registerFactory(manager);
        SecureCompareProtocol.registerFactory(manager);
        SecureMultiplyProtocol.registerFactory(manager);
        SecureAddProtocol.registerFactory(manager);
        
        // Then register self
        manager.registerProtocolFactory(PROTOCOL_TYPE, SecureMinProtocol::new, SecureMinProtocol::new);
    }
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println(msg); }
    
    private String protocolId;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private List<Integer> participants;
    private long prime;
    private int threshold;
    private String storageTag;  // null for sticky, non-null for tagged storage
    private ISecureMinListener listener;
    
    // Input parameters
    private String leftSecretId;
    private String rightSecretId;
    private String resultSecretId;
    private String rSecretId; // For multiplication
    
    // Intermediate results (secret IDs)
    private String diffSecretId;     // left - right
    private String cmpSecretId;      // left < right ? 1 : 0
    private String scaledSecretId;   // cmp * diff
    
    // Protocol state
    private enum MinPhase {
        PHASE1_SUB_AND_CMP,  // Computing diff and cmp in parallel
        PHASE2_MULTIPLY,     // Computing scaled = cmp * diff
        PHASE3_ADD,          // Computing result = right + scaled
        COMPLETE
    }
    
    private MinPhase currentPhase;
    private int completedPhase1Operations = 0;
    private boolean complete;
    private boolean successful;
    
    public SecureMinProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract parameters
        this.protocolId = (String) params.get("protocolId");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.shareStorage = (IShareStorage) params.get("shareStorage");
        this.participants = (List<Integer>) params.get("participants");
        this.prime = (Long) params.get("prime");
        this.threshold = (Integer) params.get("threshold");
        this.storageTag = (String) params.get("storageTag");
        Object listenerObj = params.get("listener");
        this.listener = (listenerObj instanceof ISecureMinListener) ? (ISecureMinListener) listenerObj : null;
        
        this.leftSecretId = (String) params.get("leftSecretId");
        this.rightSecretId = (String) params.get("rightSecretId");
        this.resultSecretId = (String) params.get("resultSecretId");
        this.rSecretId = (String) params.get("rSecretId");
        
        // Generate unique intermediate secret IDs
        this.diffSecretId = protocolId + "-diff";
        this.cmpSecretId = protocolId + "-cmp";
        this.scaledSecretId = protocolId + "-scaled";
        
        // Start Phase 1
        startPhase1();
    }
    
    /**
     * Phase 1: Compute diff = left - right AND cmp = (left < right) in parallel.
     */
    private void startPhase1() {
        currentPhase = MinPhase.PHASE1_SUB_AND_CMP;
        completedPhase1Operations = 0;
        
        debug("SecureMin [" + protocolId + "]: Phase 1 - Computing diff and cmp in parallel");
        
        // Start SecureSub: diff = left - right
        SecureSubProtocol.start(
            manager, leftSecretId, rightSecretId, diffSecretId,
            prime, participants, shareStorage, storageTag, this
        );
        
        // Start SecureCompare: cmp = (left < right)
        // Note: Uses pre-distributed "r-key" and its bits for LSB extraction
        SecureCompareProtocol.start(
            manager, leftSecretId, rightSecretId, cmpSecretId,
            "r-key", "r-key", prime, threshold, participants, shareStorage, storageTag, this
        );
    }
    
    @Override
    public void onSecureSubComplete(String subProtocolId, String resultSecretId) {
        if (currentPhase != MinPhase.PHASE1_SUB_AND_CMP) return;
        
        debug("SecureMin [" + protocolId + "]: Subtraction complete (diff computed)");
        completedPhase1Operations++;
        startPhase2();
    }
    
    @Override
    public void onSecureCompareComplete(String cmpProtocolId, String resultSecretId) {
        if (currentPhase != MinPhase.PHASE1_SUB_AND_CMP) return;
        
        debug("SecureMin [" + protocolId + "]: Comparison complete (cmp computed)");
        completedPhase1Operations++;
        startPhase2();
    }
    
    /**
     * Phase 2: Compute scaled = cmp * diff.
     */
    private void startPhase2() {
        if (completedPhase1Operations < 2) {
            return;  // Wait for both phase 1 operations to complete
        }
        
        currentPhase = MinPhase.PHASE2_MULTIPLY;
        
        debug("SecureMin [" + protocolId + "]: Phase 2 - Computing scaled = cmp * diff");
        
        // Start SecureMultiply: scaled = cmp * diff
        SecureMultiplyProtocol.start(
            manager, cmpSecretId, diffSecretId, scaledSecretId,
            rSecretId, prime, participants, shareStorage, storageTag, this
        );
    }
    
    @Override
    public void onSecureMultiplyComplete(String mulProtocolId, String resultSecretId) {
        if (currentPhase != MinPhase.PHASE2_MULTIPLY) return;
        
        debug("SecureMin [" + protocolId + "]: Multiplication complete (scaled computed)");
        startPhase3();
    }
    
    /**
     * Phase 3: Compute result = right + scaled.
     */
    private void startPhase3() {
        currentPhase = MinPhase.PHASE3_ADD;
        
        debug("SecureMin [" + protocolId + "]: Phase 3 - Computing result = right + scaled");
        
        // Start SecureAdd: result = right + scaled
        SecureAddProtocol.start(
            manager, rightSecretId, scaledSecretId, resultSecretId,
            prime, participants, shareStorage, storageTag, this
        );
    }
    
    @Override
    public void onSecureAddComplete(String addProtocolId, String resultSecretId) {
        if (currentPhase != MinPhase.PHASE3_ADD) return;
        
        debug("SecureMin [" + protocolId + "]: Addition complete - min(left, right) computed!");
        
        currentPhase = MinPhase.COMPLETE;
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureMinComplete(this.protocolId, resultSecretId);
        }
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // This meta-protocol doesn't handle messages directly
        // All messages are handled by the sub-protocols it orchestrates
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
        return PROTOCOL_TYPE;
    }
    
    @Override
    public Object getResult() {
        return successful ? resultSecretId : null;
    }
}

