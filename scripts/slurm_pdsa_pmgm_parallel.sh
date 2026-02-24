#!/bin/bash
#SBATCH --job-name=dcop_array
#SBATCH --output=slurm_logs/dcop_%A_%a.out
#SBATCH --error=slurm_logs/dcop_%A_%a.err
#SBATCH --array=1-180
#SBATCH --mem=16G
#SBATCH --cpus-per-task=8
#SBATCH --nodes=1

# DCOP Algorithm Comparison: PDSA vs PMGM vs PMAXSUM (PARALLEL VERSION)
#
# Runs 180 configurations in parallel using Slurm job arrays.
# Each array task runs one configuration (algorithm + network + timeout/rounds + agents).
#
# Usage:
#   sbatch scripts/slurm_pdsa_pmgm_parallel.sh [PROJECT_DIR]
#
# Test Matrix (180 total):
# - Timeout-based algorithms: PDSA, PMGM (2) × 2 networks × 3 timeouts × 10 agents = 120
# - Round-based algorithms: PMAXSUM (1) × 2 networks × 3 rounds × 10 agents = 60
# - Network types: RANDOM, SCALE_FREE (2)
# - Timeouts: 60s, 120s, 180s (for PDSA, PMGM)
# - Rounds: 10, 20, 30 (for PMAXSUM)
# - Agents: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 (10)
# - Problems per config: 50

set -e

# Project directory: use argument, then env var, then current directory
PROJECT_DIR="${1:-${PROJECT_DIR:-$(pwd)}}"
cd "$PROJECT_DIR"

# Create directories
mkdir -p results logs slurm_logs

# Configuration arrays
TIMEOUT_ALGORITHMS=(PDSA PMGM)     # Use timeout-based halting
ROUND_ALGORITHMS=(PMAXSUM)          # Use round-based halting
NETWORK_TYPES=(RANDOM SCALE_FREE)
TIMEOUTS=(60 120 180)               # For PDSA, PMGM
ROUNDS=(10 20 30)                   # For PMAXSUM
AGENT_COUNTS=(10 20 30 40 50 60 70 80 90 100)

# Fixed parameters
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

# Network-specific parameters
RANDOM_DENSITY=0.4
SCALEFREE_INIT=5
SCALEFREE_ADD=4

# Timestamp for this batch (use job array ID for consistency across tasks)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_BASE="comparison_${SLURM_ARRAY_JOB_ID:-$TIMESTAMP}"

# Map array task ID (1-180) to configuration
# Tasks 1-120: timeout-based algorithms (PDSA, PMGM)
# Tasks 121-180: round-based algorithms (PMAXSUM)
TASK_ID=${SLURM_ARRAY_TASK_ID:-1}

NUM_AGENTS_COUNT=${#AGENT_COUNTS[@]}           # 10
NUM_TIMEOUTS=${#TIMEOUTS[@]}                   # 3
NUM_ROUNDS=${#ROUNDS[@]}                       # 3
NUM_NETWORKS=${#NETWORK_TYPES[@]}              # 2
NUM_TIMEOUT_ALGOS=${#TIMEOUT_ALGORITHMS[@]}    # 2
NUM_ROUND_ALGOS=${#ROUND_ALGORITHMS[@]}        # 1

# Timeout-based configs: 2 algos × 2 networks × 3 timeouts × 10 agents = 120
TIMEOUT_CONFIGS=$((NUM_TIMEOUT_ALGOS * NUM_NETWORKS * NUM_TIMEOUTS * NUM_AGENTS_COUNT))

if [[ $TASK_ID -le $TIMEOUT_CONFIGS ]]; then
    # Timeout-based algorithm
    HALT_TYPE="timeout"
    IDX=$((TASK_ID - 1))
    
    AGENT_IDX=$((IDX % NUM_AGENTS_COUNT))
    IDX=$((IDX / NUM_AGENTS_COUNT))
    
    HALT_IDX=$((IDX % NUM_TIMEOUTS))
    IDX=$((IDX / NUM_TIMEOUTS))
    
    NETWORK_IDX=$((IDX % NUM_NETWORKS))
    IDX=$((IDX / NUM_NETWORKS))
    
    ALGO_IDX=$((IDX % NUM_TIMEOUT_ALGOS))
    
    ALGO=${TIMEOUT_ALGORITHMS[$ALGO_IDX]}
    HALT_VALUE=${TIMEOUTS[$HALT_IDX]}
else
    # Round-based algorithm
    HALT_TYPE="round"
    IDX=$((TASK_ID - TIMEOUT_CONFIGS - 1))
    
    AGENT_IDX=$((IDX % NUM_AGENTS_COUNT))
    IDX=$((IDX / NUM_AGENTS_COUNT))
    
    HALT_IDX=$((IDX % NUM_ROUNDS))
    IDX=$((IDX / NUM_ROUNDS))
    
    NETWORK_IDX=$((IDX % NUM_NETWORKS))
    IDX=$((IDX / NUM_NETWORKS))
    
    ALGO_IDX=$((IDX % NUM_ROUND_ALGOS))
    
    ALGO=${ROUND_ALGORITHMS[$ALGO_IDX]}
    HALT_VALUE=${ROUNDS[$HALT_IDX]}
fi

NET_TYPE=${NETWORK_TYPES[$NETWORK_IDX]}
NUM_AGENTS=${AGENT_COUNTS[$AGENT_IDX]}

# Generate output prefix
if [[ "$HALT_TYPE" == "timeout" ]]; then
    PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_t${HALT_VALUE}_n${NUM_AGENTS}"
    HALT_DISPLAY="Timeout: ${HALT_VALUE}s"
else
    PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_r${HALT_VALUE}_n${NUM_AGENTS}"
    HALT_DISPLAY="Rounds: ${HALT_VALUE}"
fi

echo "=========================================="
echo "DCOP Parallel Test - Task $TASK_ID/180"
echo "=========================================="
echo "Algorithm:    $ALGO"
echo "Network:      $NET_TYPE"
echo "$HALT_DISPLAY"
echo "Agents:       $NUM_AGENTS"
echo "Problems:     $NUM_PROBLEMS"
echo "Output:       $PREFIX"
echo "Working dir:  $(pwd)"
echo "=========================================="
echo ""

# Build command
CMD="./scripts/run_algorithm_test.sh"
CMD="$CMD --algorithm $ALGO"
CMD="$CMD --network-type $NET_TYPE"
CMD="$CMD --num-agents $NUM_AGENTS"
CMD="$CMD --domain-size $DOMAIN_SIZE"
CMD="$CMD --num-problems $NUM_PROBLEMS"
CMD="$CMD --min-cost $MIN_COST"
CMD="$CMD --max-cost $MAX_COST"
CMD="$CMD --problem-seed $PROBLEM_SEED"
CMD="$CMD --output-prefix $PREFIX"

# Halting condition
if [[ "$HALT_TYPE" == "timeout" ]]; then
    CMD="$CMD --timeout $HALT_VALUE"
else
    CMD="$CMD --last-round $HALT_VALUE"
fi

# Network-specific parameters
if [[ "$NET_TYPE" == "RANDOM" ]]; then
    CMD="$CMD --network-density $RANDOM_DENSITY"
else
    CMD="$CMD --init-clique $SCALEFREE_INIT"
    CMD="$CMD --addition $SCALEFREE_ADD"
fi

# Export problems only for task 1 (RANDOM first) and task 61 (SCALE_FREE first among timeout algos)
# and task 121 (first PMAXSUM/RANDOM) - but we can rely on same seed generating same problems
if [[ $TASK_ID -eq 1 ]] || [[ $TASK_ID -eq 61 ]]; then
    CMD="$CMD --export-problems"
    echo "Exporting problems for verification"
fi

# Run the test
echo "Running: $CMD"
echo ""
$CMD

echo ""
echo "=========================================="
echo "Task $TASK_ID/180 Complete!"
echo "=========================================="
