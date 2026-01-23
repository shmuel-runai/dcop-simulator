package utils.protocols.mpc.securecompare;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.IProtocolMessage;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.mpc.securesub.ISecureSubListener;
import utils.protocols.mpc.securesub.SecureSubProtocol;
import utils.protocols.mpc.secureknownsub.ISecureKnownSubListener;
import utils.protocols.mpc.secureknownsub.SecureKnownSubProtocol;
import utils.crypto.secretsharing.IShareStorage;

import java.util.*;

/**
 * Secure Comparison Meta-Protocol.
 * 
 * Demonstrates protocol composition by orchestrating ReconstructSecretProtocol.
 * Compares two secrets (left and right) and stores the result as shares:
 * - Result = 1 if left < right
 * - Result = 0 if left >= right
 * 
 * This version is SECURE - it does NOT reveal the actual secret values.
 * 
 * Algorithm:
 * 1. Compute [a - b] using SecureSubProtocol
 * 2. Extract sign of [a - b] using SecureCompareHalfPrimeProtocol
 *    (which uses LSB extraction on 2*(a-b))
 * 3. The sign bit tells us if a < b without revealing a or b
 * 
 * Pre-requisites:
 * - r-key and r-key[0..30] must be pre-distributed as sticky shares
 */
public class SecureCompareProtocol implements IDistributedProtocol, 
        ISecureSubListener, ISecureCompareHalfPrimeListener, ISecureKnownSubListener {
    
    public static final String PROTOCOL_TYPE = "SECURE_COMPARE";
    
    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    
    /**
     * Starts a Secure Comparison protocol.
     * 
     * @param manager The protocol manager
     * @param leftSecretId The left operand secret ID
     * @param rightSecretId The right operand secret ID
     * @param resultSecretId Where to store the comparison result
     * @param rSecretId The r-key secret ID for LSB extraction
     * @param rBitPrefix The prefix for r-key bit secrets (e.g., "r-key")
     * @param prime The field prime
     * @param threshold The Shamir threshold (unused in new version)
     * @param participants List of participant IDs
     * @param shareStorage Share storage
     * @param resultTag Optional tag for result storage (null for sticky)
     * @param listener Completion listener
     * @return The protocol ID
     */
    public static String start(DistributedProtocolManager manager,
                               String leftSecretId, String rightSecretId, String resultSecretId,
                               String rSecretId, String rBitPrefix,
                               long prime, int threshold,
                               List<Integer> participants,
                               IShareStorage shareStorage,
                               String resultTag,
                               ISecureCompareListener listener) {
        SecureCompareProtocol protocol = new SecureCompareProtocol();
        Map<String, Object> params = new HashMap<>();
        params.put("leftSecretId", leftSecretId);
        params.put("rightSecretId", rightSecretId);
        params.put("resultSecretId", resultSecretId);
        params.put("rSecretId", rSecretId);
        params.put("rBitPrefix", rBitPrefix);
        params.put("prime", prime);
        params.put("threshold", threshold);
        params.put("shareStorage", shareStorage);
        params.put("resultTag", resultTag);
        params.put("listener", listener);
        params.put("manager", manager);
        return manager.startProtocol(protocol, params, participants);
    }
    
    /**
     * Registers the factory for this protocol type and its dependencies.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        // Register dependencies
        SecureSubProtocol.registerFactory(manager);
        SecureCompareHalfPrimeProtocol.registerFactory(manager);
        SecureKnownSubProtocol.registerFactory(manager);
        
        // Register self
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            SecureCompareProtocol::new, 
            SecureCompareProtocol::new);
    }
    
    private static final boolean DEBUG = false;
    private void debug(String msg) { if (DEBUG) System.out.println("[Compare] " + msg); }
    
    // Protocol state
    private String protocolId;
    private IShareStorage shareStorage;
    private DistributedProtocolManager manager;
    private List<Integer> participants;
    private long prime;
    private ISecureCompareListener listener;
    
    private String leftSecretId;
    private String rightSecretId;
    private String resultSecretId;
    private String rSecretId;         // R-key for LSB extraction
    private String rBitPrefix;        // Prefix for r-key bit secrets
    private String cId;               // c = a - b (difference)
    private String cCmpHalfPId;       // Output of SecureCompareHalfPrimeProtocol (before inversion)
    private String resultTag;         // Optional tag for result storage
    
    private CompareState currentState;
    private boolean complete;
    private boolean successful;
    
    private enum CompareState {
        SUBTRACTING,     // Computing a - b
        COMPARING,       // Extracting sign via half-prime comparison
        INVERTING,       // Computing 1 - rawSign
        COMPLETE
    }
    
    public SecureCompareProtocol() {
        this.complete = false;
        this.successful = false;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void initialize(Map<String, Object> params) {
        this.protocolId = (String) params.get("protocolId");
        this.shareStorage = (IShareStorage) params.get("shareStorage");
        this.manager = (DistributedProtocolManager) params.get("manager");
        this.participants = (List<Integer>) params.get("participants");
        
        this.leftSecretId = (String) params.get("leftSecretId");
        this.rightSecretId = (String) params.get("rightSecretId");
        this.resultSecretId = (String) params.get("resultSecretId");
        this.rSecretId = (String) params.get("rSecretId");
        this.rBitPrefix = (String) params.get("rBitPrefix");
        this.cId = protocolId + "-c";              // c = a - b
        this.cCmpHalfPId = protocolId + "-cCmpHalfP";
        this.resultTag = (String) params.get("resultTag");
        
        Long primeObj = (Long) params.get("prime");
        this.prime = (primeObj != null) ? primeObj : 0L;
        
        Object listenerObj = params.get("listener");
        this.listener = (listenerObj instanceof ISecureCompareListener) 
            ? (ISecureCompareListener) listenerObj : null;
        
        // This is an orchestration-only protocol - always runs as initiator
        startSubtraction();
    }
    
    // =========================================================================
    // INITIATOR FLOW
    // =========================================================================
    
    /**
     * Step 1: Compute c = [a - b] using SecureSubProtocol
     */
    private void startSubtraction() {
        debug("Starting subtraction: " + leftSecretId + " - " + rightSecretId);
        currentState = CompareState.SUBTRACTING;
        
        SecureSubProtocol.start(
            manager,
            leftSecretId,
            rightSecretId,
            cId,   // c = a - b
            prime,
            participants,
            shareStorage,
            resultTag,  // Use resultTag for cleanup
            this   // Listener
        );
    }
    
    /**
     * Step 2: Extract sign of c using SecureCompareHalfPrimeProtocol
     * Stores result at cCmpHalfPId (will be inverted in step 3)
     */
    private void startHalfPrimeCompare() {
        debug("Starting half-prime comparison on c");
        currentState = CompareState.COMPARING;
        
        SecureCompareHalfPrimeProtocol.start(
            manager,
            cId,
            cCmpHalfPId,  // Store at intermediate ID
            rSecretId,    // R-key for LSB extraction
            rBitPrefix,   // Prefix for r-key bits
            prime,
            participants,
            shareStorage,
            resultTag,    // Use resultTag for cleanup
            this          // Listener
        );
    }
    
    /**
     * Step 3: Invert the result (1 - cCmpHalfP) to get final comparison result
     */
    private void startInversion() {
        debug("Starting inversion: 1 - cCmpHalfP");
        currentState = CompareState.INVERTING;
        
        SecureKnownSubProtocol.start(
            manager,
            1L,              // knownValue = 1
            cCmpHalfPId,     // secretId = cCmpHalfP
            resultSecretId,  // result = 1 - cCmpHalfP
            true,            // knownIsLeft = true (1 - x)
            prime,
            participants,
            shareStorage,
            resultTag,
            this  // Listener
        );
    }
    
    // =========================================================================
    // LISTENERS
    // =========================================================================
    
    @Override
    public void onSecureSubComplete(String subProtocolId, String resultId) {
        if (currentState != CompareState.SUBTRACTING) return;
        
        debug("Subtraction complete");
        startHalfPrimeCompare();
    }
    
    @Override
    public void onSecureCompareHalfPrimeComplete(String halfPrimeProtocolId, String resultId) {
        if (currentState != CompareState.COMPARING) return;
        
        debug("Half-prime comparison complete, starting inversion");
        startInversion();
    }
    
    @Override
    public void onSecureKnownSubComplete(String knownSubProtocolId, String resultId) {
        if (currentState != CompareState.INVERTING) return;
        
        debug("Inversion complete - comparison finished");
        currentState = CompareState.COMPLETE;
        complete = true;
        successful = true;
        
        if (listener != null) {
            listener.onSecureCompareComplete(protocolId, resultSecretId);
        }
    }
    
    // =========================================================================
    // MESSAGE HANDLING
    // =========================================================================
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        // This orchestration protocol doesn't handle messages directly
        // Sub-protocols handle their own messages
    }
    
    // =========================================================================
    // PROTOCOL INTERFACE
    // =========================================================================
    
    @Override
    public boolean isComplete() { return complete; }
    
    @Override
    public boolean isSuccessful() { return successful; }
    
    @Override
    public String getProtocolId() { return protocolId; }
    
    @Override
    public String getProtocolType() { return PROTOCOL_TYPE; }
    
    @Override
    public Object getResult() { return successful ? resultSecretId : null; }
}
