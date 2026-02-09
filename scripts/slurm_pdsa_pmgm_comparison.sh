#!/bin/bash
#SBATCH --job-name=dcop_comparison
#SBATCH --output=slurm_logs/dcop_%j.out
#SBATCH --error=slurm_logs/dcop_%j.err
#SBATCH --time=240:00:00   # 10 days (cluster has no limit)
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Algorithm Comparison: PDSA vs PMGM
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
# - Algorithms: PDSA, PMGM
# - Network types: RANDOM (density=0.4), SCALE_FREE (init=4, addition=2)
# - Timeouts: 60s, 120s, 180s
# - Agents: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100
# - Domain size: 10
# - Cost range: 0-10
# - Problems per config: 50
#
# Total configurations: 2 algos × 2 networks × 3 timeouts × 10 agent counts = 120
# Total problems: 120 × 50 = 6000

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
ALGORITHMS="PDSA PMGM"
NETWORK_TYPES="RANDOM SCALE_FREE"
TIMEOUTS="60 120 180"
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
echo "  Algorithms: $ALGORITHMS"
echo "  Network Types: $NETWORK_TYPES"
echo "  Timeouts: $TIMEOUTS"
echo "  Agent Counts: $AGENT_COUNTS"
echo "  Domain Size: $DOMAIN_SIZE"
echo "  Cost Range: [$MIN_COST, $MAX_COST]"
echo "  Problems per config: $NUM_PROBLEMS"
echo ""

# Track overall progress
TOTAL_CONFIGS=$((2 * 2 * 3 * 10))
CURRENT_CONFIG=0
START_TIME=$(date +%s)

# Export problems only for the first configuration of each network type
# (to verify consistency across runs)
FIRST_RANDOM=true
FIRST_SCALEFREE=true

for ALGO in $ALGORITHMS; do
    for NET_TYPE in $NETWORK_TYPES; do
        for TIMEOUT in $TIMEOUTS; do
            for NUM_AGENTS in $AGENT_COUNTS; do
                CURRENT_CONFIG=$((CURRENT_CONFIG + 1))
                
                # Generate output prefix
                PREFIX="${RESULTS_BASE}_${ALGO}_${NET_TYPE}_t${TIMEOUT}_n${NUM_AGENTS}"
                
                echo ""
                echo "[$CURRENT_CONFIG/$TOTAL_CONFIGS] $ALGO / $NET_TYPE / timeout=${TIMEOUT}s / agents=$NUM_AGENTS"
                
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
