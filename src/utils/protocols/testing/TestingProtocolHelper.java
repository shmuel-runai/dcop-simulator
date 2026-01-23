package utils.protocols.testing;

import utils.protocols.core.DistributedProtocolManager;
import utils.crypto.secretsharing.IShareStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for starting testing/orchestration protocols.
 * Provides convenience methods to simplify protocol initialization.
 */
public class TestingProtocolHelper {
    
    /**
     * Starts a single MPC test iteration protocol.
     * 
     * This meta-protocol orchestrates:
     * - Distribution of 3 secrets (a, b, r)
     * - Parallel execution of SecureAdd, SecureSub, SecureMultiply, SecureCompare, SecureMin, SecureInvert, and SecureIsZero
     * - Parallel reconstruction of all results
     * - Verification and pass/fail reporting
     * 
     * @param manager The protocol manager
     * @param testNumber The test iteration number
     * @param prime Prime modulus (must be 2^s - 1 for IsZero protocol)
     * @param s Exponent for prime (prime = 2^s - 1)
     * @param threshold Minimum shares needed for reconstruction
     * @param participants List of all participant agent IDs
     * @param shareStorage Share storage for this agent
     * @param listener Listener to notify when test completes
     * @return The unique protocol ID
     */
    public static String startMPCSingleTest(DistributedProtocolManager manager,
                                           int testNumber,
                                           long prime,
                                           int s,
                                           int threshold,
                                           List<Integer> participants,
                                           IShareStorage shareStorage,
                                           IMPCSingleTestListener listener) {
        // Create protocol instance
        MPCSingleTestProtocol protocol = new MPCSingleTestProtocol();
        
        // Prepare parameters
        Map<String, Object> params = new HashMap<>();
        params.put("testNumber", testNumber);
        params.put("prime", prime);
        params.put("s", s);
        params.put("threshold", threshold);
        params.put("shareStorage", shareStorage);
        params.put("listener", listener);
        params.put("manager", manager); // Pass manager so protocol can start sub-protocols
        
        return manager.startProtocol(protocol, params, participants);
    }
    
    /**
     * Starts an MPC array test iteration protocol.
     * 
     * This meta-protocol orchestrates:
     * - Generation of random-sized array (2-20 elements) with random values
     * - Distribution of all array elements as shares
     * - Execution of FindMin protocol
     * - Parallel reconstruction of min value and index
     * - Verification and pass/fail reporting
     * 
     * @param manager The protocol manager
     * @param testNumber The test iteration number
     * @param prime Prime modulus
     * @param s Exponent for prime (prime = 2^s - 1)
     * @param threshold Minimum shares needed for reconstruction
     * @param rSecretId The identifier of the r-secret for multiplication masking
     * @param participants List of all participant agent IDs
     * @param shareStorage Share storage for this agent
     * @param listener Listener to notify when test completes
     * @return The unique protocol ID
     */
    public static String startMPCArrayTest(DistributedProtocolManager manager,
                                          int testNumber,
                                          long prime,
                                          int s,
                                          int threshold,
                                          String rSecretId,
                                          List<Integer> participants,
                                          IShareStorage shareStorage,
                                          IMPCArrayTestListener listener) {
        // Create protocol instance
        MPCArrayTestProtocol protocol = new MPCArrayTestProtocol();
        
        // Prepare parameters
        Map<String, Object> params = new HashMap<>();
        params.put("testNumber", testNumber);
        params.put("prime", prime);
        params.put("s", s);
        params.put("threshold", threshold);
        params.put("rSecretId", rSecretId);
        params.put("shareStorage", shareStorage);
        params.put("listener", listener);
        params.put("manager", manager); // Pass manager so protocol can start sub-protocols
        
        return manager.startProtocol(protocol, params, participants);
    }
    
    /**
     * Registers testing protocol factories with a manager.
     * Call this once during agent initialization to enable handling of testing protocols.
     * Uses cascading registration - each test protocol registers its dependencies.
     * 
     * @param manager The protocol manager to register factories with
     */
    public static void registerTestingProtocols(DistributedProtocolManager manager) {
        // Cascading registration - these will register their dependencies
        MPCSingleTestProtocol.registerFactory(manager);
        MPCArrayTestProtocol.registerFactory(manager);
    }
}

