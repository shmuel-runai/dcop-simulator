package utils.protocols.mpc.secureiszero;

import utils.protocols.core.*;
import utils.protocols.mpc.securemultiply.ISecureMultiplyListener;
import utils.protocols.mpc.securemultiply.SecureMultiplyProtocol;
import utils.protocols.mpc.secureinvert.ISecureInvertListener;
import utils.protocols.mpc.secureinvert.SecureInvertProtocol;
import utils.crypto.secretsharing.IShareStorage;

import java.util.List;
import java.util.Map;

/**
 * Secure Is-Zero Protocol using Fermat's Little Theorem.
 * 
 * Determines if a secret value is zero using:
 *   isZero(x) = 1 - x^(p-1)
 * 
 * For prime p = 2^s - 1 (Mersenne prime), x^(p-1) = x^(2^s - 2)
 * 
 * By Fermat's Little Theorem:
 *   - If x ≠ 0: x^(p-1) ≡ 1 (mod p), so 1 - 1 = 0
 *   - If x = 0: 0^(p-1) = 0, so 1 - 0 = 1
 * 
 * Algorithm:
 *   Phase 1: result = x * x                    (1 multiply)
 *   Phase 2: for (s-2) times:
 *              result = result * x             (1 multiply)
 *              result = result * result        (1 multiply)
 *   Phase 3: result = 1 - result               (1 invert)
 * 
 * Total: 2s - 3 secure multiplications + 1 secure invert
 * For s = 31: 59 multiplications + 1 invert
 */
public class SecureIsZeroProtocol implements IDistributedProtocol,
        ISecureMultiplyListener, ISecureInvertListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_IS_ZERO";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure IsZero protocol.
     * 
     * @param manager The protocol manager
     * @param inputSecretId The ID of the secret to test for zero
     * @param resultSecretId The ID to store the result (1 if zero, 0 otherwise)
     * @param rSecretId Pre-shared r-secret for multiplication masking
     * @param prime The prime modulus (must be 2^s - 1)
     * @param s The exponent (prime = 2^s - 1)
     * @param participants List of participant IDs
     * @param shareStorage Share storage
     * @param storageTag Storage scope tag (null = sticky/permanent)
     * @param listener Completion listener
     * @return The protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               String inputSecretId, String resultSecretId, String rSecretId,
                               long prime, int s,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String storageTag,
                               ISecureIsZeroListener listener) {
        SecureIsZeroProtocol protocol = new SecureIsZeroProtocol();
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("inputSecretId", inputSecretId);
        params.put("resultSecretId", resultSecretId);
        params.put("rSecretId", rSecretId);
        params.put("prime", prime);
        params.put("s", s);
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
        SecureMultiplyProtocol.registerFactory(manager);
        SecureInvertProtocol.registerFactory(manager);
        
        // Initiator-only protocol (non-initiators just respond to sub-protocols)
        manager.registerProtocolFactory(PROTOCOL_TYPE, null, null);
    }
    
    // =========================================================================
    // INSTANCE STATE
    // =========================================================================
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println("[IsZero] " + msg); }
    
    private String protocolId;
    private int agentId;
    private DistributedProtocolManager manager;
    private IShareStorage shareStorage;
    private IMessageTransport transport;
    private List<Integer> participants;
    private long prime;
    private int s;  // Exponent: prime = 2^s - 1
    private String rSecretId;
    private String storageTag;
    private ISecureIsZeroListener listener;
    
    // Input/output
    private String inputSecretId;
    private String resultSecretId;
    
    // Protocol phases
    private enum Phase {
        PHASE_1_SQUARING,       // result = x * x
        PHASE_2_MULTIPLY_BY_X,  // result = result * x
        PHASE_2_SQUARING,       // result = result * result
        PHASE_3_INVERTING,      // result = 1 - result
        COMPLETE
    }
    
    private Phase currentPhase;
    private int currentIteration;  // 0 to s-3 (total s-2 iterations)
    private int maxIterations;     // s - 2
    
    // Current result storage ID (alternates between iterations)
    private String currentResultId;
    private String tempId;  // Temporary storage for multiply-by-x result
    
    private boolean complete;
    private boolean successful;
    
    public SecureIsZeroProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    // =========================================================================
    // INITIALIZATION
    // =========================================================================
    
    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.agentId = (Integer) params.get("agentId");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.shareStorage = (IShareStorage) params.get("shareStorage");
        this.transport = (IMessageTransport) params.get("transport");
        this.participants = (List<Integer>) params.get("participants");
        this.prime = (Long) params.get("prime");
        this.s = (Integer) params.get("s");
        this.rSecretId = (String) params.get("rSecretId");
        this.storageTag = (String) params.get("storageTag");

        Object listenerObj = params.get("listener");
        this.listener = (listenerObj instanceof ISecureIsZeroListener) ? (ISecureIsZeroListener) listenerObj : null;
        
        this.inputSecretId = (String) params.get("inputSecretId");
        this.resultSecretId = (String) params.get("resultSecretId");
        
        // Calculate iterations: s - 2
        this.maxIterations = s - 2;
        this.currentIteration = 0;
        
        // Generate base IDs for intermediate results
        this.currentResultId = "iszero-result-" + protocolId + "-0";
        this.tempId = "iszero-temp-" + protocolId;
        
        debug("Starting with s=" + s + ", maxIterations=" + maxIterations);
        debug("Input: " + inputSecretId + ", Output: " + resultSecretId);
        
        // Start the exponentiation chain
        startPhase1();
    }
    
    // =========================================================================
    // PHASE 1: result = x * x
    // =========================================================================
    
    private void startPhase1() {
        currentPhase = Phase.PHASE_1_SQUARING;
        debug("Phase 1: Computing x * x");
        
        SecureMultiplyProtocol.start(
            manager,
            inputSecretId, inputSecretId,  // x * x
            currentResultId,               // Store in result-0
            rSecretId, prime,
            participants, shareStorage, storageTag, this
        );
    }
    
    // =========================================================================
    // PHASE 2: Loop (s-2) times: result = result * x, then result = result * result
    // =========================================================================
    
    private void startPhase2MultiplyByX() {
        currentPhase = Phase.PHASE_2_MULTIPLY_BY_X;
        
        // temp = result * x
        String tempIdForIteration = tempId + "-" + currentIteration;
        debug("Phase 2." + currentIteration + ".1: Computing result * x");
        
        SecureMultiplyProtocol.start(
            manager,
            currentResultId, inputSecretId,  // result * x
            tempIdForIteration,              // Store in temp
            rSecretId, prime,
            participants, shareStorage, storageTag, this
        );
    }
    
    private void startPhase2Squaring() {
        currentPhase = Phase.PHASE_2_SQUARING;
        
        // result = temp * temp
        String tempIdForIteration = tempId + "-" + currentIteration;
        String nextResultId = "iszero-result-" + protocolId + "-" + (currentIteration + 1);
        debug("Phase 2." + currentIteration + ".2: Computing temp * temp");
        
        SecureMultiplyProtocol.start(
            manager,
            tempIdForIteration, tempIdForIteration,  // temp * temp
            nextResultId,                            // Store in next result
            rSecretId, prime,
            participants, shareStorage, storageTag, this
        );
        
        // Update current result ID for next iteration
        currentResultId = nextResultId;
    }
    
    // =========================================================================
    // PHASE 3: result = 1 - result
    // =========================================================================
    
    private void startPhase3() {
        currentPhase = Phase.PHASE_3_INVERTING;
        debug("Phase 3: Computing 1 - result");
        
        SecureInvertProtocol.start(
            manager,
            currentResultId, resultSecretId,  // 1 - result
            prime, participants, shareStorage, storageTag, this
        );
    }
    
    // =========================================================================
    // CALLBACKS
    // =========================================================================
    
    @Override
    public void onSecureMultiplyComplete(String mulProtocolId, String resultId) {
        if (complete) return;
        
        switch (currentPhase) {
            case PHASE_1_SQUARING:
                // Phase 1 done, start phase 2 iteration 0
                debug("Phase 1 complete, starting phase 2");
                if (maxIterations > 0) {
                    startPhase2MultiplyByX();
                } else {
                    // Edge case: s = 2, skip to phase 3
                    startPhase3();
                }
                break;
                
            case PHASE_2_MULTIPLY_BY_X:
                // Multiply by x done, now square
                startPhase2Squaring();
                break;
                
            case PHASE_2_SQUARING:
                // Squaring done, check if more iterations needed
                currentIteration++;
                if (currentIteration < maxIterations) {
                    startPhase2MultiplyByX();
                } else {
                    // All iterations done, move to phase 3
                    debug("Phase 2 complete after " + maxIterations + " iterations");
                    startPhase3();
                }
                break;
                
            default:
                // Ignore unexpected callbacks
                break;
        }
    }
    
    @Override
    public void onSecureInvertComplete(String invertProtocolId, String resultId) {
        if (complete) return;
        
        if (currentPhase == Phase.PHASE_3_INVERTING) {
            debug("Phase 3 complete, protocol finished");
            currentPhase = Phase.COMPLETE;
            complete = true;
            successful = true;
            
            if (listener != null) {
                listener.onSecureIsZeroComplete(protocolId, resultSecretId);
            }
        }
    }
    
    // =========================================================================
    // MESSAGE HANDLING (not used - this is an initiator-only orchestration protocol)
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // This protocol doesn't receive direct messages
        // All work is done through sub-protocols
    }
    
    // =========================================================================
    // PROTOCOL INTERFACE
    // =========================================================================
    
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
