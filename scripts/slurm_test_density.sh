#!/bin/bash
#SBATCH --job-name=dcop_density
#SBATCH --output=slurm_logs/dcop_density_%A_%a.out
#SBATCH --error=slurm_logs/dcop_density_%A_%a.err
#SBATCH --time=10:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --array=1-75

# DCOP Performance Test - Varying Network Density (Parallel via Job Array)
#
# Total tasks: 3 algorithms × 5 timeouts × 5 densities = 75
#
# Fixed: agents=30, domain_size=10, network=RANDOM
# Variable: network_density=0.2,0.4,0.6,0.8,1.0
#
# Usage:
#   sbatch scripts/slurm_test_density.sh

set -e

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

# Configuration arrays
ALGORITHMS=(PDSA PMGM PMAXSUM)
TIMEOUTS=(60 120 180 240 300)
DENSITIES=(0.2 0.4 0.6 0.8 1.0)

# Fixed parameters
NUM_AGENTS=30
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

# Calculate array sizes
NUM_ALGOS=${#ALGORITHMS[@]}
NUM_TIMEOUTS=${#TIMEOUTS[@]}
NUM_DENSITIES=${#DENSITIES[@]}

# Map SLURM_ARRAY_TASK_ID (1-75) to configuration indices
TASK_ID=${SLURM_ARRAY_TASK_ID:-1}
TASK_IDX=$((TASK_ID - 1))

# Order: densities (fastest) -> timeouts -> algorithms (slowest)
DENSITY_IDX=$((TASK_IDX % NUM_DENSITIES))
TASK_IDX=$((TASK_IDX / NUM_DENSITIES))

TIMEOUT_IDX=$((TASK_IDX % NUM_TIMEOUTS))
ALGO_IDX=$((TASK_IDX / NUM_TIMEOUTS))

# Get actual values
ALGO=${ALGORITHMS[$ALGO_IDX]}
TIMEOUT=${TIMEOUTS[$TIMEOUT_IDX]}
DENSITY=${DENSITIES[$DENSITY_IDX]}

# Results directory (shared by all tasks in this array job)
ARRAY_JOB_ID="${SLURM_ARRAY_JOB_ID:-local}"
RUN_DIR="results/density_${ARRAY_JOB_ID}"
mkdir -p "$RUN_DIR"

# First task writes config file
if [[ "$SLURM_ARRAY_TASK_ID" == "1" ]]; then
    CONFIG_FILE="$RUN_DIR/config.txt"
    {
        echo "DCOP Network Density Test (Parallel)"
        echo "====================================="
        echo ""
        echo "Test Type:     Varying Network Density (RANDOM only)"
        echo "Array Job ID:  $ARRAY_JOB_ID"
        echo "Started:       $(date)"
        echo ""
        echo "Algorithms:    ${ALGORITHMS[*]}"
        echo "Timeouts:      ${TIMEOUTS[*]}"
        echo "Agents:        $NUM_AGENTS (fixed)"
        echo "Domain Size:   $DOMAIN_SIZE (fixed)"
        echo "Densities:     ${DENSITIES[*]}"
        echo "Network:       RANDOM only"
        echo "Problems:      $NUM_PROBLEMS per config"
        echo "Total Tasks:   $((NUM_ALGOS * NUM_TIMEOUTS * NUM_DENSITIES))"
    } > "$CONFIG_FILE"
fi

DENSITY_LABEL=$(echo $DENSITY | tr -d '.')
FILE_PREFIX="${ALGO}_RANDOM_t${TIMEOUT}_nd${DENSITY_LABEL}"
echo "Task $SLURM_ARRAY_TASK_ID: $ALGO / RANDOM / ${TIMEOUT}s / density=${DENSITY}"

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

# Move results to shared directory
for file in results/test_${FILE_PREFIX}_*.csv; do
    [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
done

echo "Task $SLURM_ARRAY_TASK_ID complete!"
