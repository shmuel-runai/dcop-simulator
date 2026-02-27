#!/bin/bash
#SBATCH --job-name=dcop_domain
#SBATCH --output=slurm_logs/dcop_domain_%A_%a.out
#SBATCH --error=slurm_logs/dcop_domain_%A_%a.err
#SBATCH --time=10:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --array=1-150

# DCOP Performance Test - Varying Domain Size (Parallel via Job Array)
#
# Total tasks: 3 algorithms × 2 networks × 5 timeouts × 5 domain sizes = 150
#
# Fixed: agents=30
# Variable: domain_size=5,10,15,20,25
#
# Usage:
#   sbatch scripts/slurm_test_domain.sh

set -e

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

# Configuration arrays
ALGORITHMS=(PDSA PMGM PMAXSUM)
NETWORK_TYPES=(RANDOM SCALE_FREE)
TIMEOUTS=(60 120 180 240 300)
DOMAIN_SIZES=(5 10 15 20 25)

# Fixed parameters
NUM_AGENTS=30
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000
RANDOM_DENSITY=0.4
SCALEFREE_INIT=5
SCALEFREE_ADD=4

# Calculate array sizes
NUM_ALGOS=${#ALGORITHMS[@]}
NUM_NETS=${#NETWORK_TYPES[@]}
NUM_TIMEOUTS=${#TIMEOUTS[@]}
NUM_DOMAINS=${#DOMAIN_SIZES[@]}

# Map SLURM_ARRAY_TASK_ID (1-150) to configuration indices
TASK_ID=${SLURM_ARRAY_TASK_ID:-1}
TASK_IDX=$((TASK_ID - 1))

# Order: domains (fastest) -> timeouts -> networks -> algorithms (slowest)
DOMAIN_IDX=$((TASK_IDX % NUM_DOMAINS))
TASK_IDX=$((TASK_IDX / NUM_DOMAINS))

TIMEOUT_IDX=$((TASK_IDX % NUM_TIMEOUTS))
TASK_IDX=$((TASK_IDX / NUM_TIMEOUTS))

NET_IDX=$((TASK_IDX % NUM_NETS))
ALGO_IDX=$((TASK_IDX / NUM_NETS))

# Get actual values
ALGO=${ALGORITHMS[$ALGO_IDX]}
NET_TYPE=${NETWORK_TYPES[$NET_IDX]}
TIMEOUT=${TIMEOUTS[$TIMEOUT_IDX]}
DOMAIN_SIZE=${DOMAIN_SIZES[$DOMAIN_IDX]}

# Results directory (shared by all tasks in this array job)
ARRAY_JOB_ID="${SLURM_ARRAY_JOB_ID:-local}"
RUN_DIR="results/domain_${ARRAY_JOB_ID}"
mkdir -p "$RUN_DIR"

# First task writes config file
if [[ "$SLURM_ARRAY_TASK_ID" == "1" ]]; then
    CONFIG_FILE="$RUN_DIR/config.txt"
    {
        echo "DCOP Domain Size Test (Parallel)"
        echo "================================="
        echo ""
        echo "Test Type:     Varying Domain Size"
        echo "Array Job ID:  $ARRAY_JOB_ID"
        echo "Started:       $(date)"
        echo ""
        echo "Algorithms:    ${ALGORITHMS[*]}"
        echo "Timeouts:      ${TIMEOUTS[*]}"
        echo "Agents:        $NUM_AGENTS (fixed)"
        echo "Domain Sizes:  ${DOMAIN_SIZES[*]}"
        echo "Networks:      ${NETWORK_TYPES[*]}"
        echo "Problems:      $NUM_PROBLEMS per config"
        echo "Total Tasks:   $((NUM_ALGOS * NUM_NETS * NUM_TIMEOUTS * NUM_DOMAINS))"
    } > "$CONFIG_FILE"
fi

FILE_PREFIX="${ALGO}_${NET_TYPE}_t${TIMEOUT}_d${DOMAIN_SIZE}"
echo "Task $SLURM_ARRAY_TASK_ID: $ALGO / $NET_TYPE / ${TIMEOUT}s / domain=${DOMAIN_SIZE}"

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

# Move results to shared directory
for file in results/test_${FILE_PREFIX}_*.csv; do
    [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
done

echo "Task $SLURM_ARRAY_TASK_ID complete!"
