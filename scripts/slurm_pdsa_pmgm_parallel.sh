#!/bin/bash
#SBATCH --job-name=dcop_array
#SBATCH --output=slurm_logs/dcop_%A_%a.out
#SBATCH --error=slurm_logs/dcop_%A_%a.err
#SBATCH --array=1-120
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Algorithm Comparison: PDSA vs PMGM (PARALLEL VERSION)
#
# Runs 120 configurations in parallel using Slurm job arrays.
# Each array task runs one configuration (algorithm + network + timeout + agents).
#
# Usage:
#   sbatch scripts/slurm_pdsa_pmgm_parallel.sh [PROJECT_DIR]
#
# Test Matrix (120 total):
# - Algorithms: PDSA, PMGM (2)
# - Network types: RANDOM, SCALE_FREE (2)
# - Timeouts: 60s, 120s, 180s (3)
# - Agents: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 (10)
# - Problems per config: 50

set -e

# Project directory: use argument, then env var, then current directory
PROJECT_DIR="${1:-${PROJECT_DIR:-$(pwd)}}"
cd "$PROJECT_DIR"

# Create directories
mkdir -p results logs slurm_logs

# Configuration arrays
ALGORITHMS=(PDSA PMGM)
NETWORK_TYPES=(RANDOM SCALE_FREE)
TIMEOUTS=(60 120 180)
AGENT_COUNTS=(10 20 30 40 50 60 70 80 90 100)

# Fixed parameters
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

# Network-specific parameters
RANDOM_DENSITY=0.4
SCALEFREE_INIT=4
SCALEFREE_ADD=2

# Timestamp for this batch (use job array ID for consistency across tasks)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_BASE="comparison_${SLURM_ARRAY_JOB_ID:-$TIMESTAMP}"

# Map array task ID (1-120) to configuration
# Order: algo -> network -> timeout -> agents
TASK_ID=${SLURM_ARRAY_TASK_ID:-1}

# Calculate indices (0-based)
IDX=$((TASK_ID - 1))

NUM_AGENTS_COUNT=${#AGENT_COUNTS[@]}    # 10
NUM_TIMEOUTS=${#TIMEOUTS[@]}            # 3
NUM_NETWORKS=${#NETWORK_TYPES[@]}       # 2
NUM_ALGOS=${#ALGORITHMS[@]}             # 2

# Decompose task ID into configuration indices
AGENT_IDX=$((IDX % NUM_AGENTS_COUNT))
IDX=$((IDX / NUM_AGENTS_COUNT))

TIMEOUT_IDX=$((IDX % NUM_TIMEOUTS))
IDX=$((IDX / NUM_TIMEOUTS))

NETWORK_IDX=$((IDX % NUM_NETWORKS))
IDX=$((IDX / NUM_NETWORKS))

ALGO_IDX=$((IDX % NUM_ALGOS))

# Get actual values
ALGO=${ALGORITHMS[$ALGO_IDX]}
NET_TYPE=${NETWORK_TYPES[$NETWORK_IDX]}
TIMEOUT=${TIMEOUTS[$TIMEOUT_IDX]}
NUM_AGENTS=${AGENT_COUNTS[$AGENT_IDX]}

# Generate output prefix
PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_t${TIMEOUT}_n${NUM_AGENTS}"

echo "=========================================="
echo "DCOP Parallel Test - Task $TASK_ID/120"
echo "=========================================="
echo "Algorithm:    $ALGO"
echo "Network:      $NET_TYPE"
echo "Timeout:      ${TIMEOUT}s"
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
CMD="$CMD --timeout $TIMEOUT"
CMD="$CMD --num-problems $NUM_PROBLEMS"
CMD="$CMD --min-cost $MIN_COST"
CMD="$CMD --max-cost $MAX_COST"
CMD="$CMD --problem-seed $PROBLEM_SEED"
CMD="$CMD --output-prefix $PREFIX"

# Network-specific parameters
if [[ "$NET_TYPE" == "RANDOM" ]]; then
    CMD="$CMD --network-density $RANDOM_DENSITY"
else
    CMD="$CMD --init-clique $SCALEFREE_INIT"
    CMD="$CMD --addition $SCALEFREE_ADD"
fi

# Export problems only for task 1 (RANDOM) and task 61 (SCALE_FREE first)
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
echo "Task $TASK_ID Complete!"
echo "=========================================="
