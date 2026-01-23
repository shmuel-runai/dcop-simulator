# DCOP Testing Framework

## Overview

A modular framework for testing Distributed Constraint Optimization Problem (DCOP) algorithms using the Sinalgo network simulator.

## Architecture

```
src/dcop/
├── common/                              # Framework-agnostic
│   ├── DCOPProblem.java                 # Problem representation
│   ├── TestResults.java                 # Results collection
│   ├── AlgorithmType.java               # Algorithm enum
│   ├── configuration/
│   │   ├── ISimulationConfiguration.java
│   │   └── RandomNetworkConfiguration.java
│   ├── network/
│   │   ├── DCOPNetwork.java             # Network container
│   │   └── IDCOPNetworkBuilder.java     # Builder interface
│   └── nodes/
│       └── IDCOPAgent.java              # Agent interface
├── sinalgo/                             # Shared Sinalgo code
│   └── messages/
│       └── ValueMessage.java            # Shared message
└── algorithms/                          # Algorithm implementations
    ├── basic/
    │   ├── BasicNetworkBuilder.java
    │   └── sinalgo/nodes/
    │       └── BasicDCOPAgent.java      # Random algorithm
    └── dsa/
        ├── DSANetworkBuilder.java
        └── sinalgo/nodes/
            └── DSAAgent.java            # DSA optimization
```

## Implemented Algorithms

### 1. Basic (Random)
- Each agent selects a random value
- No optimization
- **Average Cost**: ~646 (baseline)

### 2. DSA (Distributed Stochastic Algorithm)
- Local search with probabilistic improvements
- **Parameter**: `stochastic` (default 0.8) - probability of accepting improvement
- **Average Cost**: ~278 (57% better than random!)

## Usage

### Batch Mode (CLI)

Edit `src/projects/dcopProject/Config.xml`:

```xml
<DCOPTest 
    algorithm="dsa"      <!-- or "basic" -->
    dsaStochastic="0.8"  <!-- DSA parameter -->
    numIterations="10"
    timeoutSeconds="30"
    numAgents="10"
    domainSize="5"
    minCost="0"
    maxCost="100"
    networkDensity="0.3"
/>
```

Run:
```bash
cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease
./run-batch.sh  # or use full java command
```

### GUI Mode

```bash
cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease
/opt/homebrew/opt/java/bin/java -Xmx500m \
  -cp binaries/bin:binaries/jdom.jar \
  sinalgo.runtime.Main -project dcopProject
```

Click either:
- **"Run DCOP Test (Basic)"** - Random algorithm
- **"Run DCOP Test (DSA)"** - DSA algorithm

## Adding New Algorithms

Follow this pattern:

```
src/dcop/algorithms/mgm/
├── MGMNetworkBuilder.java
└── sinalgo/nodes/
    └── MGMAgent.java
```

1. Create agent implementing `IDCOPAgent`
2. Create builder implementing `IDCOPNetworkBuilder`
3. Add to `AlgorithmType` enum
4. Add factory case in `CustomGlobal.createNetworkBuilder()`
5. Add GUI button (optional)

## Key Design Principles

- **Interface-based**: `IDCOPAgent`, `IDCOPNetworkBuilder` allow algorithm polymorphism
- **1-based indexing**: Agent IDs match Sinalgo (1 to N)
- **Separation of concerns**: common vs algorithm-specific vs sinalgo-specific
- **Extensible**: Adding new algorithms requires no modification to existing code

## Configuration

All parameters in `RandomNetworkConfiguration`:
- `numIterations`: Number of test runs
- `timeoutMs`: Max runtime per iteration (ms)
- `problemSeed`, `algorithmSeed`: Random seeds
- `numAgents`: Number of agents (N)
- `domainSize`: Value domain size (M)
- `minCost`, `maxCost`: Constraint cost range
- `networkDensity`: Edge probability [0.0, 1.0]

## Results

Results saved to `logs/dcop_results_YYYYMMDD_HHMMSS.csv`:
```csv
Iteration,Runtime_ms,TotalCost,AgentValues
1,1000,476,"[0, 3, 1, 3, 3, 2, 0, 2, 2, 1, 4]"
...
```

Console output shows:
- Per-iteration cost and runtime
- Average cost and runtime
- Configuration details

## Build & Test

```bash
# Clean and compile
ant clean compile

# Test Basic
# (Edit Config.xml: algorithm="basic")
ant compile && ./run-batch.sh

# Test DSA
# (Edit Config.xml: algorithm="dsa")
ant compile && ./run-batch.sh
```

## Next Steps

Potential algorithms to implement:
- **MGM (Maximum Gain Messages)**: Coordination-based improvement
- **ADOPT**: Tree-based optimal search
- **DPOP**: Dynamic programming optimization
- **Max-Sum**: Message-passing algorithm

## Notes

- DSA agents need `DCOPProblem` reference for cost calculation
- Basic agents don't need problem reference (random selection)
- Timer starts on first simulation round (`preRound()`)
- Each iteration resets node IDs and creates fresh network
