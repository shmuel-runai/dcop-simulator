#!/bin/bash
# Run DCOP algorithm tests with configurable parameters
#
# Usage: ./scripts/run_algorithm_test.sh [options]
#
# Options:
#   --algorithm <name>      Algorithm type: PDSA, PMGM, DSA, BASIC, MAXSUM, PMAXSUM
#   --network-type <type>   Network topology: RANDOM, SCALE_FREE
#   --num-agents <n>        Number of agents
#   --domain-size <n>       Domain size (values per agent)
#   --timeout <seconds>     Timeout per problem in seconds
#   --num-problems <n>      Number of problems to test
#   --min-cost <n>          Minimum cost value
#   --max-cost <n>          Maximum cost value
#   --network-density <f>   Network density for RANDOM networks (0.0-1.0)
#   --init-clique <n>       Initial clique size for SCALE_FREE networks
#   --addition <n>          Edges per new node for SCALE_FREE networks
#   --problem-seed <n>      Base seed for problem generation
#   --output-prefix <str>   Prefix for output files
#   --export-problems       Enable export of problem cost matrices
#   --last-round <n>        Round-based halting (-1 for timeout-based)
#   --help                  Show this help message
#
# Environment variables:
#   JAVA_CMD                Java command with options (default: java -Djava.awt.headless=true -Xmx8g)

set -e

# Default values
ALGORITHM="PDSA"
NETWORK_TYPE="RANDOM"
NUM_AGENTS=10
DOMAIN_SIZE=5
TIMEOUT=60
NUM_PROBLEMS=10
MIN_COST=0
MAX_COST=10
NETWORK_DENSITY=0.3
INIT_CLIQUE=5
ADDITION=4
PROBLEM_SEED=1000
OUTPUT_PREFIX=""
EXPORT_PROBLEMS="false"
LAST_ROUND=-1  # -1 means use timeout-based halting (not round-based)

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --algorithm)
            ALGORITHM="$2"
            shift 2
            ;;
        --network-type)
            NETWORK_TYPE="$2"
            shift 2
            ;;
        --num-agents)
            NUM_AGENTS="$2"
            shift 2
            ;;
        --domain-size)
            DOMAIN_SIZE="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        --num-problems)
            NUM_PROBLEMS="$2"
            shift 2
            ;;
        --min-cost)
            MIN_COST="$2"
            shift 2
            ;;
        --max-cost)
            MAX_COST="$2"
            shift 2
            ;;
        --network-density)
            NETWORK_DENSITY="$2"
            shift 2
            ;;
        --init-clique)
            INIT_CLIQUE="$2"
            shift 2
            ;;
        --addition)
            ADDITION="$2"
            shift 2
            ;;
        --problem-seed)
            PROBLEM_SEED="$2"
            shift 2
            ;;
        --output-prefix)
            OUTPUT_PREFIX="$2"
            shift 2
            ;;
        --export-problems)
            EXPORT_PROBLEMS="true"
            shift
            ;;
        --last-round)
            LAST_ROUND="$2"
            shift 2
            ;;
        --help)
            head -25 "$0" | tail -23
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Configuration
# Note: -Djava.awt.headless=true is required for running on headless servers/clusters
# JAVA_CMD can be overridden via environment variable
JAVA_CMD="${JAVA_CMD:-java -Djava.awt.headless=true -Xmx12g} -cp binaries/bin:binaries/jdom.jar"
MAIN_CLASS="sinalgo.runtime.Main"
PROJECT="-project dcopProject -batch"

# Create results directory
mkdir -p results
mkdir -p logs

# Print configuration
echo "=========================================="
echo "DCOP Algorithm Test"
echo "=========================================="
echo "Algorithm:       $ALGORITHM"
echo "Network Type:    $NETWORK_TYPE"
echo "Num Agents:      $NUM_AGENTS"
echo "Domain Size:     $DOMAIN_SIZE"
echo "Timeout:         ${TIMEOUT}s"
echo "Num Problems:    $NUM_PROBLEMS"
echo "Cost Range:      [$MIN_COST, $MAX_COST]"
if [[ "$NETWORK_TYPE" == "RANDOM" ]]; then
    echo "Network Density: $NETWORK_DENSITY"
else
    echo "Init Clique:     $INIT_CLIQUE"
    echo "Addition:        $ADDITION"
fi
echo "Problem Seed:    $PROBLEM_SEED"
echo "Last Round:      $LAST_ROUND"
if [[ -n "$OUTPUT_PREFIX" ]]; then
    echo "Output Prefix:   $OUTPUT_PREFIX"
fi
if [[ "$EXPORT_PROBLEMS" == "true" ]]; then
    echo "Export Problems: enabled"
fi
echo "=========================================="
echo ""

# Build command
CMD="$JAVA_CMD $MAIN_CLASS $PROJECT"
CMD="$CMD -overwrite DCOPTest/algorithm=$ALGORITHM"
CMD="$CMD -overwrite DCOPTest/networkType=$NETWORK_TYPE"
CMD="$CMD -overwrite DCOPTest/numAgents=$NUM_AGENTS"
CMD="$CMD -overwrite DCOPTest/domainSize=$DOMAIN_SIZE"
CMD="$CMD -overwrite DCOPTest/timeoutSeconds=$TIMEOUT"
CMD="$CMD -overwrite DCOPTest/numIterations=$NUM_PROBLEMS"
CMD="$CMD -overwrite DCOPTest/minCost=$MIN_COST"
CMD="$CMD -overwrite DCOPTest/maxCost=$MAX_COST"
CMD="$CMD -overwrite DCOPTest/networkDensity=$NETWORK_DENSITY"
CMD="$CMD -overwrite DCOPTest/initClique=$INIT_CLIQUE"
CMD="$CMD -overwrite DCOPTest/addition=$ADDITION"
CMD="$CMD -overwrite DCOPTest/problemSeed=$PROBLEM_SEED"
CMD="$CMD -overwrite DCOPTest/lastRound=$LAST_ROUND"

if [[ -n "$OUTPUT_PREFIX" ]]; then
    CMD="$CMD -overwrite DCOPTest/outputPrefix=$OUTPUT_PREFIX"
fi

if [[ "$EXPORT_PROBLEMS" == "true" ]]; then
    CMD="$CMD -overwrite DCOPTest/exportProblems=true"
fi

# Track timing
START_TIME=$(date +%s)

# Run simulation
echo "Running simulation..."
echo ""
$CMD

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Test Complete!"
echo "=========================================="
echo "Duration: ${DURATION} seconds"
echo ""

# List output files
if [[ -n "$OUTPUT_PREFIX" ]]; then
    echo "Output files:"
    ls -la results/test_${OUTPUT_PREFIX}_* 2>/dev/null || echo "  (no files found)"
fi
