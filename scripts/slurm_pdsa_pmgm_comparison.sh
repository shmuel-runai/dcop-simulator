#!/bin/bash
#SBATCH --job-name=dcop_comparison
#SBATCH --output=slurm_logs/dcop_%j.out
#SBATCH --error=slurm_logs/dcop_%j.err
#SBATCH --time=240:00:00   # 10 days (cluster has no limit)
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Algorithm Comparison: PDSA vs PMGM vs PMAXSUM
# 
# Usage:
#   ./scripts/slurm_pdsa_pmgm_comparison.sh [PROJECT_DIR]
#   sbatch scripts/slurm_pdsa_pmgm_comparison.sh [PROJECT_DIR]
#
# Environment variables:
#   PROJECT_DIR  - Path to dcop-simulator project (default: current directory)
#   JAVA_CMD     - Java command with options (default: java -Djava.awt.headless=true -Xmx8g)
#
# Test Matrix:
# - Algorithms: PDSA, PMGM (timeout-based), PMAXSUM (round-based)
# - Network types: RANDOM (density=0.4), SCALE_FREE (init=4, addition=2)
# - Timeouts: 60s, 120s, 180s (for PDSA, PMGM)
# - Rounds: 10, 20, 30 (for PMAXSUM - equivalent progression)
# - Agents: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100
# - Domain size: 10
# - Cost range: 0-10
# - Problems per config: 50
#
# Total configurations: 3 algos × 2 networks × 3 (timeouts/rounds) × 10 agent counts = 180
# Total problems: 180 × 50 = 9000

set -e

# Project directory: use argument, then env var, then current directory
PROJECT_DIR="${1:-${PROJECT_DIR:-$(pwd)}}"

# Change to project directory
cd "$PROJECT_DIR"
echo "Working directory: $(pwd)"

# Create directories
mkdir -p results
mkdir -p logs
mkdir -p slurm_logs

# Configuration
TIMEOUT_ALGORITHMS="PDSA PMGM"       # Algorithms that use timeout-based halting
ROUND_ALGORITHMS="PMAXSUM"            # Algorithms that use round-based halting
ALL_ALGORITHMS="PDSA PMGM PMAXSUM"
NETWORK_TYPES="RANDOM SCALE_FREE"
TIMEOUTS="60 120 180"                 # For timeout-based algorithms
ROUNDS="10 20 30"                     # For round-based algorithms (equivalent progression)
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

# Timestamp for this test run
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_BASE="comparison_${TIMESTAMP}"

echo "=========================================="
echo "DCOP Algorithm Comparison Test"
echo "Started: $(date)"
echo "=========================================="
echo ""
echo "Test Matrix:"
echo "  Timeout-based: $TIMEOUT_ALGORITHMS (timeouts: $TIMEOUTS)"
echo "  Round-based:   $ROUND_ALGORITHMS (rounds: $ROUNDS)"
echo "  Network Types: $NETWORK_TYPES"
echo "  Agent Counts:  $AGENT_COUNTS"
echo "  Domain Size:   $DOMAIN_SIZE"
echo "  Cost Range:    [$MIN_COST, $MAX_COST]"
echo "  Problems per config: $NUM_PROBLEMS"
echo ""

# Track overall progress
# 2 timeout algos × 2 networks × 3 timeouts × 10 agents = 120
# 1 round algo × 2 networks × 3 rounds × 10 agents = 60
TOTAL_CONFIGS=$((2 * 2 * 3 * 10 + 1 * 2 * 3 * 10))
CURRENT_CONFIG=0
START_TIME=$(date +%s)

# Export problems only for the first configuration of each network type
# (to verify consistency across runs)
FIRST_RANDOM=true
FIRST_SCALEFREE=true

# Helper function to run a configuration
run_config() {
    local ALGO=$1
    local NET_TYPE=$2
    local HALT_TYPE=$3  # "timeout" or "round"
    local HALT_VALUE=$4
    local NUM_AGENTS=$5
    
    CURRENT_CONFIG=$((CURRENT_CONFIG + 1))
    
    # Generate output prefix
    if [[ "$HALT_TYPE" == "timeout" ]]; then
        PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_t${HALT_VALUE}_n${NUM_AGENTS}"
        echo ""
        echo "[$CURRENT_CONFIG/$TOTAL_CONFIGS] $ALGO / $NET_TYPE / timeout=${HALT_VALUE}s / agents=$NUM_AGENTS"
    else
        PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_r${HALT_VALUE}_n${NUM_AGENTS}"
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
        # Export problems for first RANDOM config
        if $FIRST_RANDOM; then
            CMD="$CMD --export-problems"
            FIRST_RANDOM=false
        fi
    else
        CMD="$CMD --init-clique $SCALEFREE_INIT"
        CMD="$CMD --addition $SCALEFREE_ADD"
        # Export problems for first SCALE_FREE config
        if $FIRST_SCALEFREE; then
            CMD="$CMD --export-problems"
            FIRST_SCALEFREE=false
        fi
    fi
    
    # Run the test
    $CMD
    
    # Progress update
    ELAPSED=$(($(date +%s) - START_TIME))
    AVG_TIME=$((ELAPSED / CURRENT_CONFIG))
    REMAINING=$(((TOTAL_CONFIGS - CURRENT_CONFIG) * AVG_TIME))
    echo "  Progress: $CURRENT_CONFIG/$TOTAL_CONFIGS, Elapsed: ${ELAPSED}s, ETA: ${REMAINING}s"
}

# Run timeout-based algorithms (PDSA, PMGM)
for ALGO in $TIMEOUT_ALGORITHMS; do
    for NET_TYPE in $NETWORK_TYPES; do
        for TIMEOUT in $TIMEOUTS; do
            for NUM_AGENTS in $AGENT_COUNTS; do
                run_config "$ALGO" "$NET_TYPE" "timeout" "$TIMEOUT" "$NUM_AGENTS"
            done
        done
    done
done

# Run round-based algorithms (PMAXSUM)
for ALGO in $ROUND_ALGORITHMS; do
    for NET_TYPE in $NETWORK_TYPES; do
        for ROUND in $ROUNDS; do
            for NUM_AGENTS in $AGENT_COUNTS; do
                run_config "$ALGO" "$NET_TYPE" "round" "$ROUND" "$NUM_AGENTS"
            done
        done
    done
done

END_TIME=$(date +%s)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Comparison Test Complete!"
echo "=========================================="
echo "Finished: $(date)"
echo "Total Duration: ${TOTAL_DURATION} seconds ($((TOTAL_DURATION / 60)) minutes)"
echo ""
echo "Results saved with prefix: $RESULTS_BASE"
echo ""
echo "Output files:"
ls -la results/test_${RESULTS_BASE}_* 2>/dev/null | head -20
echo ""
echo "To analyze results, run:"
echo "  python scripts/verify_problems.py results/test_${RESULTS_BASE}_*_problems.csv"
echo ""
