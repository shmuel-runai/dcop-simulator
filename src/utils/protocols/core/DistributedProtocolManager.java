package utils.protocols.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Flavor of protocol to create: INITIATOR or RESPONDER.
 */
enum ProtocolFlavor {
    /** The protocol instance that starts/initiates the protocol */
    INITIATOR,
    /** The protocol instance that responds to an incoming message */
    RESPONDER
}

/**
 * Manages distributed protocol instances and message routing.
 * 
 * This is the main entry point for agents to:
 * - Start new protocol instances
 * - Route incoming protocol messages to the correct protocol
 * - Receive completion notifications
 * 
 * The manager maintains a registry of active protocols and handles
 * their lifecycle (creation, execution, completion, cleanup).
 * 
 * <h2>Design Note: Factory Responsibility</h2>
 * This class also acts as a protocol factory (via the inner {@link ProtocolFactory} class).
 * The factory was kept here rather than split into a separate class because:
 * <ul>
 *   <li>The factory is tightly coupled to message handling - it only creates protocols
 *       when an incoming message arrives for an unknown protocol ID</li>
 *   <li>Splitting would add complexity (dependency injection) with little benefit</li>
 *   <li>The factory logic is simple (just a lookup table of suppliers)</li>
 * </ul>
 * If the factory logic grows more complex in the future, consider extracting
 * the inner class to a separate file.
 * 
 * @see ProtocolFactory
 */
public class DistributedProtocolManager {
    
    /**
     * Map of protocolId to active protocol instances.
     */
    private final Map<String, IDistributedProtocol> activeProtocols;
    
    /**
     * Message transport for sending messages.
     */
    private final IMessageTransport transport;
    
    /**
     * ID of this agent.
     */
    private final int agentId;
    
    /**
     * Inner factory for creating protocol instances.
     */
    private final ProtocolFactory factory;
    
    /**
     * Creates a new protocol manager.
     * 
     * @param transport Message transport implementation
     * @param agentId ID of this agent
     */
    public DistributedProtocolManager(IMessageTransport transport, int agentId) {
        this.activeProtocols = new HashMap<>();
        this.transport = transport;
        this.agentId = agentId;
        this.factory = new ProtocolFactory();
    }
    
    /**
     * Registers protocol factories for creating instances.
     * 
     * @param protocolType The protocol type identifier (e.g., "SECURE_ADD")
     * @param initiatorSupplier Factory for initiator instances (can be null if not factory-created)
     * @param responderSupplier Factory for responder instances (required for incoming messages)
     */
    public void registerProtocolFactory(String protocolType, 
                                        Supplier<IDistributedProtocol> initiatorSupplier,
                                        Supplier<IDistributedProtocol> responderSupplier) {
        factory.register(protocolType, initiatorSupplier, responderSupplier);
    }
    
    /**
     * Starts a new protocol instance.
     * Caller creates the protocol instance and provides parameters.
     * Manager handles registration, initialization, and lifecycle.
     * 
     * @param protocol The protocol instance to start
     * @param params Protocol-specific parameters
     * @param participants List of all participant agent IDs (including this agent)
     * @return The unique protocol instance ID
     * @throws ProtocolException if initialization fails
     */
    public String startProtocol(IDistributedProtocol protocol, Map<String, Object> params, 
                               List<Integer> participants) {
        // Generate unique protocol ID (unless already specified in params)
        String protocolId = (String) params.get("protocolId");
        if (protocolId == null) {
            protocolId = UUID.randomUUID().toString();
        }
        
        // Add protocol ID and participants to params
        params.put("protocolId", protocolId);
        params.put("participants", participants);
        params.put("agentId", agentId);
        params.put("transport", transport);
        params.put("manager", this);
        
        // Register the protocol BEFORE initialization
        // (initialization may trigger message broadcasts that loop back to this agent)
        activeProtocols.put(protocolId, protocol);
        
        // Initialize the protocol
        protocol.initialize(params);
        
        // Check if protocol completed during initialization
        // (can happen with synchronous message passing)
        checkProtocolCompletion(protocolId);
        
        return protocolId;
    }
    
    /**
     * Handles an incoming protocol message.
     * Routes the message to the appropriate protocol instance.
     * 
     * @param msg The protocol message
     * @param senderId ID of the sending agent
     */
    public void handleIncomingMessage(IProtocolMessage msg, int senderId) {
        handleIncomingMessage(msg, senderId, null);
    }
    
    /**
     * Handles an incoming protocol message with optional protocol-specific resources.
     * Routes the message to the appropriate protocol instance.
     * 
     * @param msg The protocol message
     * @param senderId ID of the sending agent
     * @param protocolResources Optional map of protocol-specific resources (e.g., shareStorage for MPC protocols)
     */
    public void handleIncomingMessage(IProtocolMessage msg, int senderId, Map<String, Object> protocolResources) {
        String protocolId = msg.getProtocolId();
        
        // Check if we have an active protocol for this ID
        IDistributedProtocol protocol = activeProtocols.get(protocolId);
        
        if (protocol == null) {
            // This might be a new protocol initiated by another agent
            // Create a new protocol reactor instance to handle it
            protocol = createProtocolForIncomingMessage(msg, protocolResources, protocolId);
            if (protocol == null) {
                // Stale message for a completed protocol - ignore it
                return;
            }
        }
        
        // Deliver message to protocol
        protocol.handleMessage(msg, senderId);
        
        // Check if protocol completed
        checkProtocolCompletion(protocolId);
    }
    
    // =========================================================================
    // PROTOCOL CREATION (delegated to inner ProtocolFactory)
    // =========================================================================
    
    /**
     * Creates a protocol instance for an incoming message.
     * Used when we receive a message for a protocol we haven't started yet.
     * 
     * IMPORTANT: Registers the protocol BEFORE calling initialize() to handle
     * self-message loopback during initialization.
     * 
     * @param msg The incoming message
     * @param protocolResources Optional protocol-specific resources
     * @param protocolId The protocol ID (for registration)
     * @return A new protocol instance, or null if cannot be created
     */
    private IDistributedProtocol createProtocolForIncomingMessage(IProtocolMessage msg, 
                                                                   Map<String, Object> protocolResources,
                                                                   String protocolId) {
        // Completion messages should only be handled by existing protocols
        // If the protocol doesn't exist, the completion message is stale
        if (msg.isCompletionMessage()) {
            return null;
        }
        
        // Create RESPONDER protocol based on message type (delegated to inner factory)
        IDistributedProtocol protocol = factory.create(msg.getProtocolType(), ProtocolFlavor.RESPONDER);
        
        // Build params map - start with base params
        Map<String, Object> params = new HashMap<>();
        params.put("protocolId", protocolId);
        params.put("agentId", agentId);
        params.put("transport", transport);
        params.put("manager", this);
        
        // Add protocol-specific resources if provided
        if (protocolResources != null) {
            params.putAll(protocolResources);
        }
        
        // Extract protocol-specific parameters from the message (self-describing)
        params.putAll(msg.extractParams());
        
        // Derive participants from transport if not already provided
        // ASSUMPTION: Full-mesh network where all agents are neighbors.
        // In a non-full-mesh network, the initiator's participants list may differ
        // from what we derive here. For proper support of sparse networks, the
        // initiator should include participants in the message (via extractParams).
        if (!params.containsKey("participants")) {
            params.put("participants", transport.getParticipants());
        }
        
        // Register the protocol BEFORE initialization
        // (initialization may trigger message broadcasts that loop back to this agent)
        activeProtocols.put(protocolId, protocol);
        
        // Initialize the protocol with available params
        protocol.initialize(params);
        
        return protocol;
    }
    
    /**
     * Checks if a protocol has completed and cleans up if so.
     * 
     * @param protocolId The protocol ID to check
     */
    private void checkProtocolCompletion(String protocolId) {
        IDistributedProtocol protocol = activeProtocols.get(protocolId);
        
        if (protocol != null && protocol.isComplete()) {
            // Remove from active protocols (cleanup)
            activeProtocols.remove(protocolId);
        }
    }
    
    /**
     * Gets the number of active protocols.
     * Useful for debugging and testing.
     * 
     * @return Number of active protocols
     */
    public int getActiveProtocolCount() {
        return activeProtocols.size();
    }
    
    /**
     * Checks if a specific protocol is active.
     * 
     * @param protocolId The protocol ID
     * @return true if the protocol is active, false otherwise
     */
    public boolean hasActiveProtocol(String protocolId) {
        return activeProtocols.containsKey(protocolId);
    }
    
    /**
     * Gets the ID of the agent this manager belongs to.
     * 
     * @return The agent ID
     */
    public int getAgentId() {
        return agentId;
    }
    
    /**
     * Removes a completed protocol from the active protocols map.
     * This should be called after a protocol has finished to free up resources.
     * 
     * @param protocolId The ID of the protocol to remove
     * @return true if the protocol was removed, false if it wasn't found
     */
    public boolean removeProtocol(String protocolId) {
        return activeProtocols.remove(protocolId) != null;
    }
    
    /**
     * Clears all completed protocols from the active protocols map.
     * This is useful for cleanup between test iterations.
     * 
     * @return Number of protocols removed
     */
    public int clearCompletedProtocols() {
        final int[] removed = {0};
        activeProtocols.entrySet().removeIf(entry -> {
            if (entry.getValue().isComplete()) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }
    
    /**
     * Clears all active protocols (completed or not).
     * Use with caution - typically used for complete cleanup/reset.
     * 
     * @return Number of protocols removed
     */
    public int clearAllProtocols() {
        int count = activeProtocols.size();
        activeProtocols.clear();
        return count;
    }
    
    // =========================================================================
    // INNER CLASS: ProtocolFactory
    // =========================================================================
    
    /**
     * Inner class that handles protocol instantiation.
     * 
     * <p>This encapsulates the factory pattern for creating protocol instances
     * from incoming messages. It maintains a registry of protocol type -> supplier
     * mappings and creates instances on demand.</p>
     * 
     * <p>Kept as an inner class (rather than a separate file) because:
     * <ul>
     *   <li>It's only used by the enclosing manager</li>
     *   <li>The logic is simple (lookup table + creation)</li>
     *   <li>Avoids unnecessary file proliferation</li>
     * </ul>
     * If this grows more complex, consider extracting to a separate file.</p>
     */
    private static class ProtocolFactory {
        
        /**
         * Registry of initiator factories.
         * Maps protocolType -> factory function for creating initiator instances.
         * May be null if initiators are created directly (not via factory).
         */
        private final Map<String, Supplier<IDistributedProtocol>> initiatorSuppliers;
        
        /**
         * Registry of responder factories.
         * Maps protocolType -> factory function for creating responder instances.
         */
        private final Map<String, Supplier<IDistributedProtocol>> responderSuppliers;
        
        ProtocolFactory() {
            this.initiatorSuppliers = new HashMap<>();
            this.responderSuppliers = new HashMap<>();
        }
        
        /**
         * Registers suppliers for a protocol type.
         * 
         * Idempotent: if the protocol type is already registered, this method
         * silently returns without modifying the existing registration.
         * This allows protocols to safely declare their dependencies without
         * worrying about duplicate registrations.
         * 
         * @param protocolType The protocol type identifier
         * @param initiatorSupplier Factory for initiator instances (can be null if created directly)
         * @param responderSupplier Factory for responder instances (can be null for initiator-only protocols)
         */
        void register(String protocolType, 
                      Supplier<IDistributedProtocol> initiatorSupplier,
                      Supplier<IDistributedProtocol> responderSupplier) {
            // Idempotent: skip if already registered
            if (initiatorSuppliers.containsKey(protocolType) || responderSuppliers.containsKey(protocolType)) {
                return;
            }
            // Store non-null suppliers
            if (initiatorSupplier != null) {
                initiatorSuppliers.put(protocolType, initiatorSupplier);
            }
            if (responderSupplier != null) {
                responderSuppliers.put(protocolType, responderSupplier);
            }
        }
        
        /**
         * Creates a protocol instance for the given type and flavor.
         * 
         * @param protocolType The protocol type
         * @param flavor INITIATOR or RESPONDER
         * @return A new protocol instance
         * @throws ProtocolException if no factory registered for the type/flavor
         */
        IDistributedProtocol create(String protocolType, ProtocolFlavor flavor) {
            Map<String, Supplier<IDistributedProtocol>> suppliers = 
                (flavor == ProtocolFlavor.INITIATOR) ? initiatorSuppliers : responderSuppliers;
            
            Supplier<IDistributedProtocol> supplier = suppliers.get(protocolType);
            if (supplier == null) {
                throw new ProtocolException("No " + flavor + " factory registered for protocol type: " + protocolType);
            }
            return supplier.get();
        }
    }
}

