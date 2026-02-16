#!/bin/bash
#SBATCH --job-name=dcop_agents
#SBATCH --output=slurm_logs/dcop_agents_%j.out
#SBATCH --error=slurm_logs/dcop_agents_%j.err
#SBATCH --time=48:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Performance Test - Varying Agent Count
#
# Fixed: domain_size=10
# Variable: agents=10,20,30,40,50,60,70,80,90,100
#
# Usage:
#   ./scripts/slurm_test_agents.sh [OPTIONS]
#   sbatch scripts/slurm_test_agents.sh [OPTIONS]
#
# Options:
#   --algorithms "ALG1 ALG2 ..."   Algorithms to test (default: "PDSA PMGM PMAXSUM")

set -e

ALGORITHMS_INPUT="${1:-PDSA PMGM PMAXSUM}"
PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"

cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

# Create results directory
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
JOB_ID="${SLURM_JOB_ID:-local}"
RUN_DIR="results/agents_${JOB_ID}_${TIMESTAMP}"
mkdir -p "$RUN_DIR"

# Configuration
NETWORK_TYPES="RANDOM SCALE_FREE"
TIMEOUTS="60 120 180"
AGENT_COUNTS="10 20 30 40 50 60 70 80 90 100"
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000
RANDOM_DENSITY=0.4
SCALEFREE_INIT=4
SCALEFREE_ADD=2

# Write config file
CONFIG_FILE="$RUN_DIR/config.txt"
{
    echo "DCOP Agent Count Test"
    echo "====================="
    echo ""
    echo "Test Type: Varying Agent Count"
    echo "Job ID:    $JOB_ID"
    echo "Started:   $(date)"
    echo ""
    echo "Algorithms:    $ALGORITHMS_INPUT"
    echo "Timeouts:      $TIMEOUTS"
    echo "Agent Counts:  $AGENT_COUNTS"
    echo "Domain Size:   $DOMAIN_SIZE (fixed)"
    echo "Networks:      $NETWORK_TYPES"
    echo "Problems:      $NUM_PROBLEMS per config"
} > "$CONFIG_FILE"

echo "Results directory: $RUN_DIR"
echo ""

for ALGO in $ALGORITHMS_INPUT; do
    for NET_TYPE in $NETWORK_TYPES; do
        for TIMEOUT in $TIMEOUTS; do
            for NUM_AGENTS in $AGENT_COUNTS; do
                FILE_PREFIX="${ALGO}_${NET_TYPE}_t${TIMEOUT}_n${NUM_AGENTS}"
                echo "Running: $ALGO / $NET_TYPE / ${TIMEOUT}s / ${NUM_AGENTS} agents"
                
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
                CMD="$CMD --timeout $TIMEOUT"
                
                if [[ "$NET_TYPE" == "RANDOM" ]]; then
                    CMD="$CMD --network-density $RANDOM_DENSITY"
                else
                    CMD="$CMD --init-clique $SCALEFREE_INIT"
                    CMD="$CMD --addition $SCALEFREE_ADD"
                fi
                
                $CMD
                
                # Move results
                for file in results/test_${FILE_PREFIX}_*.csv; do
                    [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
                done
            done
        done
    done
done

echo ""
echo "Complete! Results in: $RUN_DIR"
echo "Finished: $(date)" >> "$CONFIG_FILE"
