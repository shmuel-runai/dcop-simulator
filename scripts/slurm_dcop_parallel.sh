#!/bin/bash
#SBATCH --job-name=dcop_array
#SBATCH --output=slurm_logs/dcop_%A_%a.out
#SBATCH --error=slurm_logs/dcop_%A_%a.err
#SBATCH --mem=16G
#SBATCH --cpus-per-task=8
#SBATCH --nodes=1

# DCOP Algorithm Comparison - Parallel Version
#
# This script runs a single configuration based on SLURM_ARRAY_TASK_ID.
# Use with sbatch --array to run multiple configurations in parallel.
#
# Usage:
#   # Run only PMAXSUM (60 configs = 1 algo × 2 networks × 3 timeouts × 10 agents)
#   sbatch --array=1-60 scripts/slurm_dcop_parallel.sh --algorithms "PMAXSUM"
#
#   # Run PDSA and PMGM (120 configs = 2 algos × 2 networks × 3 timeouts × 10 agents)
#   sbatch --array=1-120 scripts/slurm_dcop_parallel.sh --algorithms "PDSA PMGM"
#
#   # Run all algorithms (180 configs)
#   sbatch --array=1-180 scripts/slurm_dcop_parallel.sh --algorithms "PDSA PMGM PMAXSUM"
#
# Options:
#   --algorithms "ALG1 ALG2 ..."   Algorithms to test (REQUIRED)
#   --project-dir PATH             Project directory (default: current directory)
#
# Configuration counts (all use timeout-based halting):
#   PDSA:     60 configs (1 × 2 networks × 3 timeouts × 10 agents)
#   PMGM:     60 configs (1 × 2 networks × 3 timeouts × 10 agents)
#   PMAXSUM:  60 configs (1 × 2 networks × 3 timeouts × 10 agents)

set -e

# Default values
ALGORITHMS_INPUT=""
PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --algorithms)
            ALGORITHMS_INPUT="$2"
            shift 2
            ;;
        --project-dir)
            PROJECT_DIR="$2"
            shift 2
            ;;
        --help)
            head -35 "$0" | tail -30
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$ALGORITHMS_INPUT" ]]; then
    echo "ERROR: --algorithms is required"
    echo "Example: sbatch --array=1-60 scripts/slurm_dcop_parallel.sh --algorithms \"PMAXSUM\""
    exit 1
fi

cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

# Separate into timeout-based and round-based
TIMEOUT_ALGORITHMS=""
ROUND_ALGORITHMS=""

for ALGO in $ALGORITHMS_INPUT; do
    case $ALGO in
        PDSA|PMGM|PMAXSUM)
            # All privacy-preserving algorithms use timeout for performance testing
            TIMEOUT_ALGORITHMS="$TIMEOUT_ALGORITHMS $ALGO"
            ;;
        MAXSUM)
            # Non-private Max-Sum uses rounds (for correctness verification only)
            ROUND_ALGORITHMS="$ROUND_ALGORITHMS $ALGO"
            ;;
        *)
            echo "ERROR: Unknown algorithm: $ALGO"
            echo "Supported: PDSA, PMGM, PMAXSUM, MAXSUM"
            exit 1
            ;;
    esac
done

TIMEOUT_ALGORITHMS=$(echo $TIMEOUT_ALGORITHMS | xargs)
ROUND_ALGORITHMS=$(echo $ROUND_ALGORITHMS | xargs)

# Configuration arrays
NETWORK_TYPES=(RANDOM SCALE_FREE)
TIMEOUTS=(60 120 180)
ROUNDS=(10 20 30)
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

# Create unique results directory with array job ID
ARRAY_JOB_ID="${SLURM_ARRAY_JOB_ID:-local_$(date +%Y%m%d_%H%M%S)}"
RUN_DIR="results/run_${ARRAY_JOB_ID}"
mkdir -p "$RUN_DIR"

# Count configurations
TIMEOUT_ALGO_ARRAY=($TIMEOUT_ALGORITHMS)
ROUND_ALGO_ARRAY=($ROUND_ALGORITHMS)
NUM_TIMEOUT_ALGOS=${#TIMEOUT_ALGO_ARRAY[@]}
NUM_ROUND_ALGOS=${#ROUND_ALGO_ARRAY[@]}
NUM_AGENTS_COUNT=${#AGENT_COUNTS[@]}
NUM_TIMEOUTS=${#TIMEOUTS[@]}
NUM_ROUNDS=${#ROUNDS[@]}
NUM_NETWORKS=${#NETWORK_TYPES[@]}

TIMEOUT_CONFIGS=$((NUM_TIMEOUT_ALGOS * NUM_NETWORKS * NUM_TIMEOUTS * NUM_AGENTS_COUNT))
ROUND_CONFIGS=$((NUM_ROUND_ALGOS * NUM_NETWORKS * NUM_ROUNDS * NUM_AGENTS_COUNT))
TOTAL_CONFIGS=$((TIMEOUT_CONFIGS + ROUND_CONFIGS))

# Get task ID
TASK_ID=${SLURM_ARRAY_TASK_ID:-1}

if [[ $TASK_ID -gt $TOTAL_CONFIGS ]]; then
    echo "ERROR: Task ID $TASK_ID exceeds total configs $TOTAL_CONFIGS"
    echo "Use --array=1-$TOTAL_CONFIGS"
    exit 1
fi

# Map task ID to configuration
if [[ $TASK_ID -le $TIMEOUT_CONFIGS ]] && [[ $NUM_TIMEOUT_ALGOS -gt 0 ]]; then
    HALT_TYPE="timeout"
    IDX=$((TASK_ID - 1))
    
    AGENT_IDX=$((IDX % NUM_AGENTS_COUNT))
    IDX=$((IDX / NUM_AGENTS_COUNT))
    
    HALT_IDX=$((IDX % NUM_TIMEOUTS))
    IDX=$((IDX / NUM_TIMEOUTS))
    
    NETWORK_IDX=$((IDX % NUM_NETWORKS))
    IDX=$((IDX / NUM_NETWORKS))
    
    ALGO_IDX=$((IDX % NUM_TIMEOUT_ALGOS))
    
    ALGO=${TIMEOUT_ALGO_ARRAY[$ALGO_IDX]}
    HALT_VALUE=${TIMEOUTS[$HALT_IDX]}
else
    HALT_TYPE="round"
    IDX=$((TASK_ID - TIMEOUT_CONFIGS - 1))
    
    AGENT_IDX=$((IDX % NUM_AGENTS_COUNT))
    IDX=$((IDX / NUM_AGENTS_COUNT))
    
    HALT_IDX=$((IDX % NUM_ROUNDS))
    IDX=$((IDX / NUM_ROUNDS))
    
    NETWORK_IDX=$((IDX % NUM_NETWORKS))
    IDX=$((IDX / NUM_NETWORKS))
    
    ALGO_IDX=$((IDX % NUM_ROUND_ALGOS))
    
    ALGO=${ROUND_ALGO_ARRAY[$ALGO_IDX]}
    HALT_VALUE=${ROUNDS[$HALT_IDX]}
fi

NET_TYPE=${NETWORK_TYPES[$NETWORK_IDX]}
NUM_AGENTS=${AGENT_COUNTS[$AGENT_IDX]}

# Generate output prefix (just file naming, not directory)
if [[ "$HALT_TYPE" == "timeout" ]]; then
    FILE_PREFIX="${ALGO}_${NET_TYPE}_t${HALT_VALUE}_n${NUM_AGENTS}"
    HALT_DISPLAY="Timeout: ${HALT_VALUE}s"
else
    FILE_PREFIX="${ALGO}_${NET_TYPE}_r${HALT_VALUE}_n${NUM_AGENTS}"
    HALT_DISPLAY="Rounds: ${HALT_VALUE}"
fi

# Write config file on first task
CONFIG_FILE="$RUN_DIR/config.txt"
if [[ $TASK_ID -eq 1 ]] && [[ ! -f "$CONFIG_FILE" ]]; then
    {
        echo "DCOP Parallel Test Configuration"
        echo "================================="
        echo ""
        echo "Run Info:"
        echo "  Array Job ID: $ARRAY_JOB_ID"
        echo "  Started:      $(date)"
        echo "  Host:         $(hostname)"
        echo "  Directory:    $(pwd)"
        echo ""
        echo "Algorithms:"
        if [[ -n "$TIMEOUT_ALGORITHMS" ]]; then
            echo "  Timeout-based: $TIMEOUT_ALGORITHMS"
            echo "  Timeouts:      ${TIMEOUTS[*]}"
        fi
        if [[ -n "$ROUND_ALGORITHMS" ]]; then
            echo "  Round-based:   $ROUND_ALGORITHMS"
            echo "  Rounds:        ${ROUNDS[*]}"
        fi
        echo ""
        echo "Test Parameters:"
        echo "  Network Types:    ${NETWORK_TYPES[*]}"
        echo "  Agent Counts:     ${AGENT_COUNTS[*]}"
        echo "  Domain Size:      $DOMAIN_SIZE"
        echo "  Cost Range:       [$MIN_COST, $MAX_COST]"
        echo "  Problems/Config:  $NUM_PROBLEMS"
        echo "  Problem Seed:     $PROBLEM_SEED"
        echo ""
        echo "Network Parameters:"
        echo "  Random Density:   $RANDOM_DENSITY"
        echo "  Scale-Free Init:  $SCALEFREE_INIT"
        echo "  Scale-Free Add:   $SCALEFREE_ADD"
        echo ""
        echo "Total Configurations: $TOTAL_CONFIGS"
        echo "Total Problems:       $((TOTAL_CONFIGS * NUM_PROBLEMS))"
    } > "$CONFIG_FILE"
fi

echo "=========================================="
echo "DCOP Parallel Test - Task $TASK_ID/$TOTAL_CONFIGS"
echo "=========================================="
echo "Algorithm:    $ALGO"
echo "Network:      $NET_TYPE"
echo "$HALT_DISPLAY"
echo "Agents:       $NUM_AGENTS"
echo "Problems:     $NUM_PROBLEMS"
echo "Results Dir:  $RUN_DIR"
echo "Output File:  ${FILE_PREFIX}_results.csv"
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
CMD="$CMD --output-prefix $FILE_PREFIX"

if [[ "$HALT_TYPE" == "timeout" ]]; then
    CMD="$CMD --timeout $HALT_VALUE"
else
    CMD="$CMD --last-round $HALT_VALUE"
fi

if [[ "$NET_TYPE" == "RANDOM" ]]; then
    CMD="$CMD --network-density $RANDOM_DENSITY"
else
    CMD="$CMD --init-clique $SCALEFREE_INIT"
    CMD="$CMD --addition $SCALEFREE_ADD"
fi

# Export problems for first task of each network type
if [[ $TASK_ID -eq 1 ]]; then
    CMD="$CMD --export-problems"
    echo "Exporting problems for verification"
fi

echo "Running: $CMD"
echo ""
$CMD

# Move result files to run directory
for file in results/test_${FILE_PREFIX}_*.csv; do
    if [[ -f "$file" ]]; then
        mv "$file" "$RUN_DIR/"
        echo "Moved: $(basename $file) -> $RUN_DIR/"
    fi
done

echo ""
echo "=========================================="
echo "Task $TASK_ID/$TOTAL_CONFIGS Complete!"
echo "Results saved to: $RUN_DIR"
echo "=========================================="
