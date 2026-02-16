#!/bin/bash
#SBATCH --job-name=dcop_density
#SBATCH --output=slurm_logs/dcop_density_%j.out
#SBATCH --error=slurm_logs/dcop_density_%j.err
#SBATCH --time=48:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=1

# DCOP Performance Test - Varying Network Density
#
# Fixed: agents=30, domain_size=10, network=RANDOM
# Variable: network_density=0.2,0.4,0.6,0.8,1.0
#
# Usage:
#   ./scripts/slurm_test_density.sh [OPTIONS]
#   sbatch scripts/slurm_test_density.sh [OPTIONS]
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
RUN_DIR="results/density_${JOB_ID}_${TIMESTAMP}"
mkdir -p "$RUN_DIR"

# Configuration
TIMEOUTS="60 120 180"
NUM_AGENTS=30
DOMAIN_SIZE=10
DENSITIES="0.2 0.4 0.6 0.8 1.0"
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

# Write config file
CONFIG_FILE="$RUN_DIR/config.txt"
{
    echo "DCOP Network Density Test"
    echo "========================="
    echo ""
    echo "Test Type: Varying Network Density (RANDOM only)"
    echo "Job ID:    $JOB_ID"
    echo "Started:   $(date)"
    echo ""
    echo "Algorithms:    $ALGORITHMS_INPUT"
    echo "Timeouts:      $TIMEOUTS"
    echo "Agents:        $NUM_AGENTS (fixed)"
    echo "Domain Size:   $DOMAIN_SIZE (fixed)"
    echo "Densities:     $DENSITIES"
    echo "Network:       RANDOM only"
    echo "Problems:      $NUM_PROBLEMS per config"
} > "$CONFIG_FILE"

echo "Results directory: $RUN_DIR"
echo ""

for ALGO in $ALGORITHMS_INPUT; do
    for TIMEOUT in $TIMEOUTS; do
        for DENSITY in $DENSITIES; do
            # Convert density to filename-safe format (0.4 -> 04)
            DENSITY_LABEL=$(echo $DENSITY | tr -d '.')
            FILE_PREFIX="${ALGO}_RANDOM_t${TIMEOUT}_nd${DENSITY_LABEL}"
            echo "Running: $ALGO / RANDOM / ${TIMEOUT}s / density=${DENSITY}"
            
            CMD="./scripts/run_algorithm_test.sh"
            CMD="$CMD --algorithm $ALGO"
            CMD="$CMD --network-type RANDOM"
            CMD="$CMD --num-agents $NUM_AGENTS"
            CMD="$CMD --domain-size $DOMAIN_SIZE"
            CMD="$CMD --num-problems $NUM_PROBLEMS"
            CMD="$CMD --min-cost $MIN_COST"
            CMD="$CMD --max-cost $MAX_COST"
            CMD="$CMD --problem-seed $PROBLEM_SEED"
            CMD="$CMD --output-prefix $FILE_PREFIX"
            CMD="$CMD --timeout $TIMEOUT"
            CMD="$CMD --network-density $DENSITY"
            
            $CMD
            
            # Move results
            for file in results/test_${FILE_PREFIX}_*.csv; do
                [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
            done
        done
    done
done

echo ""
echo "Complete! Results in: $RUN_DIR"
echo "Finished: $(date)" >> "$CONFIG_FILE"
