# DCOP Simulator

A simulation framework for **Distributed Constraint Optimization Problem (DCOP)** algorithms, built on the [Sinalgo](http://www.sinalgo.ethz.ch/) network simulator.

## Features

- Multiple DCOP algorithm implementations
- Privacy-preserving variants using secure Multi-Party Computation (MPC)
- Visual GUI for interactive simulation
- Configurable problem generation (agents, domains, constraints)
- Results logging to CSV

## Implemented Algorithms

| Algorithm | Description | Privacy |
|-----------|-------------|---------|
| **Basic** | Random value selection (baseline) | No |
| **DSA** | Distributed Stochastic Algorithm - local search with probabilistic improvements | No |
| **P-DSA** | Privacy-preserving DSA using secure MPC protocols | Yes |
| **P-MGM** | Privacy-preserving Maximum Gain Messages | Yes |

## Prerequisites

- **Java 8+** (tested with OpenJDK)
- **Apache Ant** (for building)

On macOS with Homebrew:
```bash
brew install openjdk ant
```

## Quick Start

### 1. Build
```bash
ant compile
```

### 2. Run GUI
```bash
./run_gui.sh
```

Or manually:
```bash
java -Xmx8g -cp binaries/bin:binaries/jdom.jar sinalgo.runtime.Main -project dcopProject
```

### 3. In the GUI
- Click one of the algorithm buttons (e.g., "Run DCOP Test (P-DSA)")
- Watch the simulation run
- Results are saved to `logs/`

## Configuration

Edit `src/projects/dcopProject/Config.xml` to configure:

```xml
<DCOPTest 
    algorithm="pdsa"        <!-- basic, dsa, pdsa, pmgm -->
    dsaStochastic="0.8"     <!-- DSA probability parameter -->
    numIterations="15"      <!-- Number of test runs -->
    timeoutSeconds="60"     <!-- Max time per iteration -->
    numAgents="10"          <!-- Number of agents -->
    domainSize="5"          <!-- Values per agent domain -->
    minCost="0"             <!-- Min constraint cost -->
    maxCost="100"           <!-- Max constraint cost -->
    networkDensity="0.3"    <!-- Edge probability [0-1] -->
/>
```

## Project Structure

```
dcop-simulator/
├── src/
│   ├── dcop/                    # DCOP framework
│   │   ├── algorithms/          # Algorithm implementations
│   │   │   ├── basic/           # Random baseline
│   │   │   ├── dsa/             # DSA algorithm
│   │   │   ├── pdsa/            # Privacy-preserving DSA
│   │   │   └── pmgm/            # Privacy-preserving MGM
│   │   └── common/              # Shared interfaces & utilities
│   ├── utils/
│   │   ├── crypto/              # Cryptographic primitives
│   │   └── protocols/           # Secure MPC protocols
│   ├── sinalgo/                 # Sinalgo framework (modified)
│   └── projects/
│       └── dcopProject/         # Main DCOP project
├── binaries/                    # Compiled classes & libs
├── logs/                        # Result CSV files
├── build.xml                    # Ant build file
└── run_gui.sh                   # Launch script
```

## Results

Results are saved to `logs/dcop_results_<ALGORITHM>_<TIMESTAMP>.csv`:

```csv
Iteration,Runtime_ms,TotalCost,AgentValues
1,1000,476,"[0, 3, 1, 3, 3, 2, 0, 2, 2, 1, 4]"
```

## Adding New Algorithms

1. Create algorithm folder: `src/dcop/algorithms/<name>/`
2. Implement agent class extending `IDCOPAgent`
3. Implement network builder implementing `IDCOPNetworkBuilder`
4. Add to `AlgorithmType` enum
5. Add factory case in `CustomGlobal.createNetworkBuilder()`
6. (Optional) Add GUI button

See `src/dcop/README.md` for detailed architecture documentation.

## License

Based on Sinalgo, which is licensed under BSD. See [license.txt](license.txt) for details.
