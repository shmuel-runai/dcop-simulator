package utils.protocols.common.barrier;

import utils.protocols.core.IDistributedProtocol;
import utils.protocols.core.IMessageTransport;
import utils.protocols.core.DistributedProtocolManager;
import utils.protocols.core.IProtocolMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BarrierProtocol - Synchronization barrier for distributed agents.
 * 
 * Each agent creates its own BarrierProtocol instance, but all instances
 * use the same protocol ID so that messages are routed correctly between them.
 * When an agent completes its work, it signals the barrier (broadcasts to all).
 * When all N agents have signaled, each instance fires its callback locally.
 * 
 * Protocol ID format: barrier-{name}
 */
public class BarrierProtocol implements IDistributedProtocol {
    
    public static final String PROTOCOL_TYPE = "BARRIER";
    
    /**
     * Registers the factory for this protocol type.
     * No dependencies to register.
     */
    public static void registerFactory(DistributedProtocolManager manager) {
        manager.registerProtocolFactory(PROTOCOL_TYPE, 
            () -> new BarrierProtocol(), 
            () -> new BarrierProtocol());
    }
    
    // Debug logging
    private static final boolean DEBUG = false;
    private static final int DEBUG_AGENT_ID = 7; // -1 = all agents
    
    private int agentID;
    
    private void debug(String message) {
        if (DEBUG && (DEBUG_AGENT_ID == -1 || agentID == DEBUG_AGENT_ID)) {
            System.out.println(message);
        }
    }
    
    private void panic(String message) {
        throw new RuntimeException("Barrier FATAL [Agent " + agentID + "]: " + message);
    }
    
    // Protocol identity
    private String protocolId;
    
    // Protocol infrastructure
    private IMessageTransport transport;
    private List<Integer> participants;
    private IBarrierListener listener;
    
    // State tracking
    private Set<Integer> signalsReceived;  // Track which agents have signaled
    private boolean signalSent;            // Have we sent our signal?
    
    // Completion state
    private boolean complete;
    private boolean successful;
    
    /**
     * Default constructor for factory pattern.
     */
    public BarrierProtocol() {
        this.signalsReceived = new HashSet<>();
        this.signalSent = false;
        this.complete = false;
        this.successful = false;
    }
    
    /**
     * Computes a protocol ID for a barrier with a given name.
     * All agents use the SAME protocol ID so messages route to the shared barrier.
     * 
     * @param name A unique name for this barrier instance
     * @return The barrier protocol ID
     */
    public static String computeProtocolId(String name) {
        return "barrier-" + name;
    }
    
    @Override
    public void initialize(Map<String, Object> params) {
        // Extract protocol parameters
        this.protocolId = (String) params.get("protocolId");
        this.transport = (IMessageTransport) params.get("transport");
        this.participants = (List<Integer>) params.get("participants");
        
        // Extract agent context
        this.agentID = (Integer) params.get("agentId");
        
        // Extract barrier-specific params
        if (params.containsKey("listener")) {
            this.listener = (IBarrierListener) params.get("listener");
        }
        
        debug("Barrier initialized: agent=" + agentID + ", id=" + protocolId + 
              ", participants=" + participants.size());
    }
    
    /**
     * Signals that this agent has reached the barrier.
     * Broadcasts a signal to all participants.
     */
    public void signal() {
        if (signalSent) {
            panic("Barrier already signaled: " + protocolId);
            return;
        }
        
        debug("Agent " + agentID + " signaling barrier: " + protocolId);
        
        signalSent = true;
        
        // Broadcast signal to all participants (including self)
        BarrierSignalMessage msg = new BarrierSignalMessage(protocolId);
        for (int participantId : participants) {
            transport.sendMessage(msg, participantId);
        }
    }
    
    @Override
    public void handleMessage(IProtocolMessage msg, int senderId) {
        if (msg instanceof BarrierSignalMessage) {
            handleSignal((BarrierSignalMessage) msg, senderId);
        }
    }
    
    private void handleSignal(BarrierSignalMessage msg, int senderId) {
        if (signalsReceived.contains(senderId)) {
            panic("Duplicate signal from agent " + senderId);
            return;
        }
        
        signalsReceived.add(senderId);
        
        int expected = participants.size();
        debug("Agent " + agentID + " received barrier signal from " + senderId + 
              " (" + signalsReceived.size() + "/" + expected + ")");
        
        if (signalsReceived.size() < expected) {
            return;
        }
        
        complete = true;
        successful = true;
        
        debug("Barrier COMPLETE: " + protocolId);
        
        if (listener != null) {
            listener.onBarrierComplete(protocolId);
        }
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
        return "BARRIER";
    }
    
    @Override
    public Object getResult() {
        return null;
    }
}
