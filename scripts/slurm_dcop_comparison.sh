#!/bin/bash
#SBATCH --job-name=dcop_comparison
#SBATCH --output=slurm_logs/dcop_%j.out
#SBATCH --error=slurm_logs/dcop_%j.err
#SBATCH --time=240:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Algorithm Comparison Test
# 
# Usage:
#   ./scripts/slurm_dcop_comparison.sh [OPTIONS]
#   sbatch scripts/slurm_dcop_comparison.sh [OPTIONS]
#
# Options:
#   --algorithms "ALG1 ALG2 ..."   Algorithms to test (default: "PDSA PMGM PMAXSUM")
#   --project-dir PATH             Project directory (default: current directory)
#   --help                         Show this help
#
# Examples:
#   # Run all algorithms
#   ./scripts/slurm_dcop_comparison.sh
#
#   # Run only PMAXSUM
#   ./scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"
#
#   # Run PDSA and PMGM
#   ./scripts/slurm_dcop_comparison.sh --algorithms "PDSA PMGM"
#
# Supported Algorithms:
#   PDSA     - Privacy-preserving DSA (timeout-based)
#   PMGM     - Privacy-preserving MGM (timeout-based)
#   PMAXSUM  - Privacy-preserving Max-Sum (round-based)
#
# Test Matrix per algorithm:
# - Network types: RANDOM (density=0.4), SCALE_FREE (init=4, addition=2)
# - Timeouts: 60s, 120s, 180s (for PDSA, PMGM)
# - Rounds: 10, 20, 30 (for PMAXSUM)
# - Agents: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100
# - Domain size: 10
# - Cost range: 0-10
# - Problems per config: 50

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
            head -45 "$0" | tail -40
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Change to project directory
cd "$PROJECT_DIR"
echo "Working directory: $(pwd)"

# Create directories
mkdir -p results logs slurm_logs

# Parse algorithms - default to all if not specified
if [[ -z "$ALGORITHMS_INPUT" ]]; then
    ALGORITHMS_INPUT="PDSA PMGM PMAXSUM"
fi

# Separate into timeout-based and round-based
TIMEOUT_ALGORITHMS=""
ROUND_ALGORITHMS=""

for ALGO in $ALGORITHMS_INPUT; do
    case $ALGO in
        PDSA|PMGM)
            TIMEOUT_ALGORITHMS="$TIMEOUT_ALGORITHMS $ALGO"
            ;;
        PMAXSUM|MAXSUM)
            ROUND_ALGORITHMS="$ROUND_ALGORITHMS $ALGO"
            ;;
        *)
            echo "ERROR: Unknown algorithm: $ALGO"
            echo "Supported: PDSA, PMGM, PMAXSUM"
            exit 1
            ;;
    esac
done

# Trim leading spaces
TIMEOUT_ALGORITHMS=$(echo $TIMEOUT_ALGORITHMS | xargs)
ROUND_ALGORITHMS=$(echo $ROUND_ALGORITHMS | xargs)

# Configuration
NETWORK_TYPES="RANDOM SCALE_FREE"
TIMEOUTS="60 120 180"
ROUNDS="10 20 30"
AGENT_COUNTS="10 20 30 40 50 60 70 80 90 100"
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

# Network-specific parameters
RANDOM_DENSITY=0.4
SCALEFREE_INIT=4
SCALEFREE_ADD=2

# Create unique results directory with job ID and timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
JOB_ID="${SLURM_JOB_ID:-local}"
RUN_DIR="results/run_${JOB_ID}_${TIMESTAMP}"
mkdir -p "$RUN_DIR"

RESULTS_BASE="${JOB_ID}_${TIMESTAMP}"

# Count configurations
NUM_TIMEOUT_ALGOS=$(echo $TIMEOUT_ALGORITHMS | wc -w | xargs)
NUM_ROUND_ALGOS=$(echo $ROUND_ALGORITHMS | wc -w | xargs)
TIMEOUT_CONFIGS=$((NUM_TIMEOUT_ALGOS * 2 * 3 * 10))
ROUND_CONFIGS=$((NUM_ROUND_ALGOS * 2 * 3 * 10))
TOTAL_CONFIGS=$((TIMEOUT_CONFIGS + ROUND_CONFIGS))

echo "=========================================="
echo "DCOP Algorithm Comparison Test"
echo "Started: $(date)"
echo "=========================================="
echo ""
echo "Algorithms:"
if [[ -n "$TIMEOUT_ALGORITHMS" ]]; then
    echo "  Timeout-based: $TIMEOUT_ALGORITHMS (timeouts: $TIMEOUTS)"
fi
if [[ -n "$ROUND_ALGORITHMS" ]]; then
    echo "  Round-based:   $ROUND_ALGORITHMS (rounds: $ROUNDS)"
fi
echo ""
echo "Test Matrix:"
echo "  Network Types: $NETWORK_TYPES"
echo "  Agent Counts:  $AGENT_COUNTS"
echo "  Domain Size:   $DOMAIN_SIZE"
echo "  Cost Range:    [$MIN_COST, $MAX_COST]"
echo "  Problems per config: $NUM_PROBLEMS"
echo ""
echo "Total configurations: $TOTAL_CONFIGS"
echo "Total problems: $((TOTAL_CONFIGS * NUM_PROBLEMS))"
echo ""
echo "Results directory: $RUN_DIR"
echo ""

# Write configuration file
CONFIG_FILE="$RUN_DIR/config.txt"
{
    echo "DCOP Comparison Test Configuration"
    echo "==================================="
    echo ""
    echo "Run Info:"
    echo "  Job ID:     $JOB_ID"
    echo "  Timestamp:  $TIMESTAMP"
    echo "  Started:    $(date)"
    echo "  Host:       $(hostname)"
    echo "  Directory:  $(pwd)"
    echo ""
    echo "Algorithms:"
    if [[ -n "$TIMEOUT_ALGORITHMS" ]]; then
        echo "  Timeout-based: $TIMEOUT_ALGORITHMS"
        echo "  Timeouts:      $TIMEOUTS"
    fi
    if [[ -n "$ROUND_ALGORITHMS" ]]; then
        echo "  Round-based:   $ROUND_ALGORITHMS"
        echo "  Rounds:        $ROUNDS"
    fi
    echo ""
    echo "Test Parameters:"
    echo "  Network Types:    $NETWORK_TYPES"
    echo "  Agent Counts:     $AGENT_COUNTS"
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
echo "Configuration saved to: $CONFIG_FILE"
echo ""

# Track progress
CURRENT_CONFIG=0
START_TIME=$(date +%s)

# Export problems only for first configuration of each network type
FIRST_RANDOM=true
FIRST_SCALEFREE=true

# Helper function to run a configuration
run_config() {
    local ALGO=$1
    local NET_TYPE=$2
    local HALT_TYPE=$3
    local HALT_VALUE=$4
    local NUM_AGENTS=$5
    
    CURRENT_CONFIG=$((CURRENT_CONFIG + 1))
    
    # Generate output prefix (file will go into RUN_DIR)
    if [[ "$HALT_TYPE" == "timeout" ]]; then
        FILE_PREFIX="${ALGO}_${NET_TYPE}_t${HALT_VALUE}_n${NUM_AGENTS}"
        echo ""
        echo "[$CURRENT_CONFIG/$TOTAL_CONFIGS] $ALGO / $NET_TYPE / timeout=${HALT_VALUE}s / agents=$NUM_AGENTS"
    else
        FILE_PREFIX="${ALGO}_${NET_TYPE}_r${HALT_VALUE}_n${NUM_AGENTS}"
        echo ""
        echo "[$CURRENT_CONFIG/$TOTAL_CONFIGS] $ALGO / $NET_TYPE / rounds=${HALT_VALUE} / agents=$NUM_AGENTS"
    fi
    
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
    
    # Halting condition
    if [[ "$HALT_TYPE" == "timeout" ]]; then
        CMD="$CMD --timeout $HALT_VALUE"
    else
        CMD="$CMD --last-round $HALT_VALUE"
    fi
    
    # Network-specific parameters
    if [[ "$NET_TYPE" == "RANDOM" ]]; then
        CMD="$CMD --network-density $RANDOM_DENSITY"
        if $FIRST_RANDOM; then
            CMD="$CMD --export-problems"
            FIRST_RANDOM=false
        fi
    else
        CMD="$CMD --init-clique $SCALEFREE_INIT"
        CMD="$CMD --addition $SCALEFREE_ADD"
        if $FIRST_SCALEFREE; then
            CMD="$CMD --export-problems"
            FIRST_SCALEFREE=false
        fi
    fi
    
    # Run the test
    $CMD
    
    # Move result files to run directory
    for file in results/test_${FILE_PREFIX}_*.csv; do
        if [[ -f "$file" ]]; then
            mv "$file" "$RUN_DIR/"
            echo "  Moved: $(basename $file) -> $RUN_DIR/"
        fi
    done
    
    # Progress update
    ELAPSED=$(($(date +%s) - START_TIME))
    if [[ $CURRENT_CONFIG -gt 0 ]]; then
        AVG_TIME=$((ELAPSED / CURRENT_CONFIG))
        REMAINING=$(((TOTAL_CONFIGS - CURRENT_CONFIG) * AVG_TIME))
        echo "  Progress: $CURRENT_CONFIG/$TOTAL_CONFIGS, Elapsed: ${ELAPSED}s, ETA: ${REMAINING}s"
    fi
}

# Run timeout-based algorithms (PDSA, PMGM)
if [[ -n "$TIMEOUT_ALGORITHMS" ]]; then
    for ALGO in $TIMEOUT_ALGORITHMS; do
        for NET_TYPE in $NETWORK_TYPES; do
            for TIMEOUT in $TIMEOUTS; do
                for NUM_AGENTS in $AGENT_COUNTS; do
                    run_config "$ALGO" "$NET_TYPE" "timeout" "$TIMEOUT" "$NUM_AGENTS"
                done
            done
        done
    done
fi

# Run round-based algorithms (PMAXSUM)
if [[ -n "$ROUND_ALGORITHMS" ]]; then
    for ALGO in $ROUND_ALGORITHMS; do
        for NET_TYPE in $NETWORK_TYPES; do
            for ROUND in $ROUNDS; do
                for NUM_AGENTS in $AGENT_COUNTS; do
                    run_config "$ALGO" "$NET_TYPE" "round" "$ROUND" "$NUM_AGENTS"
                done
            done
        done
    done
fi

END_TIME=$(date +%s)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Comparison Test Complete!"
echo "=========================================="
echo "Finished: $(date)"
echo "Total Duration: ${TOTAL_DURATION} seconds ($((TOTAL_DURATION / 60)) minutes)"
echo ""
echo "Results directory: $RUN_DIR"
echo ""

# Append completion info to config file
{
    echo ""
    echo "Completion:"
    echo "  Finished:     $(date)"
    echo "  Duration:     ${TOTAL_DURATION} seconds ($((TOTAL_DURATION / 60)) minutes)"
    echo "  Exit Status:  Success"
} >> "$CONFIG_FILE"

# List output files
echo "Output files:"
ls -la "$RUN_DIR"/*.csv 2>/dev/null | head -20
echo ""
echo "Total files: $(ls -1 "$RUN_DIR"/*.csv 2>/dev/null | wc -l) CSV files"
echo ""
