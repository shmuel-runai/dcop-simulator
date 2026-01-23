# MPC Test Suite - Distributed Protocol Framework

## Overview

This Sinalgo project demonstrates a distributed protocol framework for Multi-Party Computation (MPC) operations. It creates a fully meshed network of 10 nodes and runs automated tests of secret sharing and secure computation protocols.

## Features

### Network Topology
- **10 nodes** in a fully meshed network
- Every node connected to every other node
- Full duplex communication

### Test Suite
- **100 automated test iterations**
- Each iteration tests the complete MPC workflow:
  1. **Share Distribution**: Node 1 distributes two random secrets (a and b) to all nodes
  2. **Secure Addition**: All nodes locally compute shares for c = a + b
  3. **Secret Reconstruction**: Node 1 collects shares and reconstructs c
  4. **Verification**: Confirms that c = a + b
  5. **Cleanup**: Removes completed protocols before next iteration

### Distributed Protocols

#### 1. Share Distribution Protocol
- Initiator generates shares using Shamir's Secret Sharing
- Distributes unique shares to each participant
- Threshold: k=3 (minimum shares needed to reconstruct)

#### 2. Secure Add MPC Protocol
- All nodes perform local computation on their shares
- No shares are exchanged (privacy-preserving)
- Result: Each node has a valid share of the sum

#### 3. Reconstruct Secret Protocol
- Initiator collects shares from all participants
- Uses Lagrange interpolation to reconstruct secret
- Only initiator learns the reconstructed value

## Project Structure

```
src/projects/mpcTest/
├── Config.xml                              # Sinalgo configuration
├── CustomGlobal.java                       # Project initialization
├── description.txt                         # Project description
├── README.md                               # This file
├── nodes/
│   └── nodeImplementations/
│       └── MPCTestNode.java               # Node with protocol manager
└── models/
    └── connectivityModels/
        └── FullMesh.java                   # Full mesh connectivity
```

## Running the Test Suite

### Option 1: Using the launcher script (macOS/Linux)
```bash
./runMPCTest.sh
```

### Option 2: Manual launch
```bash
cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease

/opt/homebrew/opt/java/bin/java -cp binaries/bin:binaries/jdom.jar \
    sinalgo.Run \
    -project mpcTest \
    -gen 10 MPCTestNode FullMesh \
    -batch
```

### Option 3: GUI Mode
```bash
/opt/homebrew/opt/java/bin/java -cp binaries/bin:binaries/jdom.jar sinalgo.Run
```
Then select "mpcTest" project and manually add 10 MPCTestNode nodes.

## Expected Output

The test suite will print progress every 10 iterations:

```
=== MPC Test Suite Starting ===
Coordinator: Node 1
Total iterations: 100
Network size: 10 nodes
Threshold: 3
Prime: 15485863
================================

Starting test iteration 1...
Starting test iteration 11...
Test 10 PASSED (a=12345, b=67890, result=80235)
...
Test 100 PASSED (a=42, b=58, result=100)

================================
=== MPC Test Suite Complete ===
================================
Total tests: 100
Passed: 100
Failed: 0
Success rate: 100.0%
Average test time: 45 ms
Total time: 4500 ms
================================
```

## Technical Details

### Cryptographic Parameters
- **Prime**: 15485863 (for finite field arithmetic)
- **Threshold**: 3 (minimum shares to reconstruct)
- **Participants**: 10 (all nodes in the network)

### Node Implementation
- Each node has a `DistributedProtocolManager`
- Integrates with Sinalgo via `SinalgoMessageTransport` adapter
- Uses `ShareStorageManager` for local share storage
- Implements `IProtocolListener` for protocol completion notifications

### Message Flow
```
ProtocolMessage (POJO)
    ↓ wrapped by
SinalgoProtocolMessageWrapper (extends sinalgo.Message)
    ↓ transmitted via
SinalgoMessageTransport
    ↓ unwrapped and routed by
DistributedProtocolManager
    ↓ processed by
Protocol Implementation (SecureAdd, Reconstruct, etc.)
```

### Framework Decoupling
- Core protocol logic is framework-agnostic
- Only the adapter layer (`adapters/`) knows about Sinalgo
- Protocols can be ported to other frameworks by implementing new adapters

## Cleanup Mechanism

After each test iteration, the system:
1. Calls `protocolManager.clearCompletedProtocols()`
2. Removes all completed protocol instances
3. Frees up memory and prevents protocol ID collisions
4. Resets test state for the next iteration

## Performance Considerations

- **Message Overhead**: Each iteration sends ~30 messages (distribution + addition + reconstruction)
- **Computation**: Primarily polynomial evaluation and Lagrange interpolation
- **Memory**: Protocols are cleaned up after completion to prevent memory leaks
- **Scalability**: O(n²) messages for full mesh communication

## Troubleshooting

### Compilation Issues
Ensure all protocol framework classes are compiled:
```bash
cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease
./compile.sh
```

### No Output
Check that:
- Config.xml has `outputToConsole` set to `true`
- Node 1 is created (it's the test coordinator)
- All 10 nodes are in the network

### Tests Failing
- Check that all nodes have the correct connectivity
- Verify that messages are being delivered
- Check console output for error messages

## Extension Points

### Adding New Protocols
1. Implement `IDistributedProtocol`
2. Create message classes implementing `IProtocolMessage`
3. Register factory in `ProtocolHelper.registerMPCProtocols()`
4. Add helper method in `ProtocolHelper` (optional)

### Changing Test Parameters
Edit `MPCTestNode.java`:
- `MAX_ITERATIONS`: Number of test iterations
- `THRESHOLD`: Shamir threshold value
- `PRIME`: Finite field prime

### Different Network Topologies
Create a new connectivity model in:
`src/projects/mpcTest/models/connectivityModels/`

## Credits

Built on top of:
- Sinalgo simulation framework
- Custom distributed protocol framework
- Shamir's Secret Sharing implementation


