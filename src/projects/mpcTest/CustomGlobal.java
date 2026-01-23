package projects.mpcTest;

import sinalgo.runtime.AbstractCustomGlobal;
import sinalgo.runtime.Runtime;
import sinalgo.nodes.Node;
import projects.mpcTest.nodes.nodeImplementations.MPCTestNode;
import sinalgo.nodes.Position;
import utils.crypto.secretsharing.ShareGenerator;
import utils.crypto.secretsharing.IShareStorage;

import java.util.Random;

/**
 * Custom global methods for the MPC test project.
 * Provides initialization and utility functions.
 */
public class CustomGlobal extends AbstractCustomGlobal {
    
    private static boolean initialized = false;
    
    /**
     * Called to check if the simulation has terminated.
     */
    @Override
    public boolean hasTerminated() {
        // Check if simulation should stop (will be set by node 1 when tests complete)
        return !sinalgo.runtime.Global.isRunning;
    }
    
    /**
     * Called before each round in batch mode.
     * We use this to initialize the topology on the first call.
     */
    @Override
    public void preRun() {
        if (!initialized) {
            initialized = true;
            createInitialTopology();
        }
    }
    
    /**
     * Called to generate the initial network topology.
     * Creates 10 MPCTestNodes with full mesh connectivity.
     */
    private static void createInitialTopology() {
        System.out.println("Creating MPC test network topology...");
        
        int numNodes = 10;
        Random rng = new Random(42);
        
        // Create 10 nodes
        for (int i = 0; i < numNodes; i++) {
            try {
                // Create node
                MPCTestNode node = new MPCTestNode();
                
                // Set random position for visualization
                double x = 100 + rng.nextDouble() * 800;
                double y = 100 + rng.nextDouble() * 800;
                node.setPosition(new Position(x, y, 0));
                
                // Initialize and add to runtime
                node.finishInitializationWithDefaultModels(true);
                
            } catch (Exception e) {
                System.err.println("Error creating node: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Created " + numNodes + " nodes");
        
        // Connect all nodes in a full mesh
        System.out.println("Creating full mesh connections...");
        int connectionCount = 0;
        for (Node from : Runtime.nodes) {
            for (Node to : Runtime.nodes) {
                if (from.ID != to.ID) {
                    from.outgoingConnections.add(from, to, true);
                    connectionCount++;
                }
            }
        }
        
        System.out.println("Created " + connectionCount + " connections");
        
        // Distribute r-secret shares to all nodes for array tests
        distributeRSecret(numNodes);
        
        // Distribute r-key and bit shares for secure comparison (LSB protocol)
        distributeRKey(numNodes);
        
        System.out.println("Topology creation complete!\n");
    }
    
    /**
     * Distributes r-secret shares to all nodes.
     * This secret is used for SecureMultiply operations in array tests (FindMin/FindMax).
     * 
     * @param numNodes Number of nodes in the network
     */
    private static void distributeRSecret(int numNodes) {
        System.out.println("Distributing r-secret shares...");
        
        // Constants matching MPCTestNode
        long PRIME = 2147483647L; // 2^31 - 1 (Mersenne prime)
        int THRESHOLD = 3;
        
        Random rng = new Random(12345); // Fixed seed for reproducibility
        
        // Generate random r-secret value
        long rSecretValue = Math.abs(rng.nextLong()) % PRIME;
        
        // Create share generator
        ShareGenerator rSecretGen = new ShareGenerator(rSecretValue, THRESHOLD, PRIME, rng);
        
        // Distribute shares to each node
        for (Node node : Runtime.nodes) {
            if (node instanceof MPCTestNode) {
                MPCTestNode mpcNode = (MPCTestNode) node;
                IShareStorage storage = mpcNode.getShareStorage();
                
                // Generate share for this node (shares are 1-indexed, node.ID is 1-based)
                storage.storeStickyShare("r-secret", rSecretGen.generateShare(node.ID));
            }
        }
        
        System.out.println("Distributed r-secret shares to " + numNodes + " nodes");
    }
    
    /**
     * Distributes r-key and bit shares to all nodes.
     * Required for the secure LSB-based comparison protocol.
     * 
     * @param numNodes Number of nodes in the network
     */
    private static void distributeRKey(int numNodes) {
        System.out.println("Distributing r-key and bit shares...");
        
        // Constants matching MPCTestNode
        long PRIME = 2147483647L; // 2^31 - 1 (Mersenne prime)
        int THRESHOLD = 3;
        int NUM_BITS = 31;
        
        Random rng = new Random(54321); // Different seed from r-secret
        
        // Generate random r-key value
        long rKeyValue = Math.abs(rng.nextLong()) % PRIME;
        
        // Create share generator for r-key
        ShareGenerator rKeyGen = new ShareGenerator(rKeyValue, THRESHOLD, PRIME, rng);
        
        // Create share generators for each bit of r-key
        ShareGenerator[] bitGens = new ShareGenerator[NUM_BITS];
        for (int bitIndex = 0; bitIndex < NUM_BITS; bitIndex++) {
            long bitValue = (rKeyValue >> bitIndex) & 1L;
            bitGens[bitIndex] = new ShareGenerator(bitValue, THRESHOLD, PRIME, rng);
        }
        
        // Distribute shares to each node
        for (Node node : Runtime.nodes) {
            if (node instanceof MPCTestNode) {
                MPCTestNode mpcNode = (MPCTestNode) node;
                IShareStorage storage = mpcNode.getShareStorage();
                
                // Generate and store r-key share
                storage.storeStickyShare("r-key", rKeyGen.generateShare(node.ID));
                
                // Generate and store bit shares
                for (int bitIndex = 0; bitIndex < NUM_BITS; bitIndex++) {
                    String bitId = "r-key[" + bitIndex + "]";
                    storage.storeStickyShare(bitId, bitGens[bitIndex].generateShare(node.ID));
                }
            }
        }
        
        System.out.println("Distributed r-key and " + NUM_BITS + " bit shares to " + numNodes + " nodes");
    }
}

