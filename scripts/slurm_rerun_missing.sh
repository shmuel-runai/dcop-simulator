#!/bin/bash
#SBATCH --job-name=dcop_rerun
#SBATCH --output=slurm_logs/dcop_rerun_%A_%a.out
#SBATCH --error=slurm_logs/dcop_rerun_%A_%a.err
#SBATCH --time=10:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --array=1-8

# Rerun only the missing PMAXSUM_RANDOM results
#
# Missing due to StackOverflow bug (now fixed):
#   t60/n10, t120/n10, t180/n10, t240/n10, t300/n10
# Missing due to time limit:
#   t240/n90, t300/n90, t300/n100
#
# Usage:
#   sbatch scripts/slurm_rerun_missing.sh

set -e

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

# Fixed parameters
ALGORITHM="PMAXSUM"
NETWORK_TYPE="RANDOM"
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000
RANDOM_DENSITY=0.4

# Define the 8 missing configurations as "timeout:agents" pairs
MISSING_CONFIGS=(
    "60:10"
    "120:10"
    "180:10"
    "240:10"
    "300:10"
    "240:90"
    "300:90"
    "300:100"
)

TASK_IDX=$((${SLURM_ARRAY_TASK_ID:-1} - 1))
CONFIG="${MISSING_CONFIGS[$TASK_IDX]}"
TIMEOUT="${CONFIG%%:*}"
NUM_AGENTS_VAL="${CONFIG##*:}"

RESULTS_DIR="${RESULTS_DIR:-results}"

FILE_PREFIX="${ALGORITHM}_${NETWORK_TYPE}_t${TIMEOUT}_n${NUM_AGENTS_VAL}"
echo "Rerun task $SLURM_ARRAY_TASK_ID: $ALGORITHM / $NETWORK_TYPE / ${TIMEOUT}s / ${NUM_AGENTS_VAL} agents"

CMD="./scripts/run_algorithm_test.sh"
CMD="$CMD --algorithm $ALGORITHM"
CMD="$CMD --network-type $NETWORK_TYPE"
CMD="$CMD --num-agents $NUM_AGENTS_VAL"
CMD="$CMD --domain-size $DOMAIN_SIZE"
CMD="$CMD --num-problems $NUM_PROBLEMS"
CMD="$CMD --min-cost $MIN_COST"
CMD="$CMD --max-cost $MAX_COST"
CMD="$CMD --problem-seed $PROBLEM_SEED"
CMD="$CMD --output-prefix $FILE_PREFIX"
CMD="$CMD --timeout $TIMEOUT"
CMD="$CMD --network-density $RANDOM_DENSITY"

$CMD

# Move results to target directory
for file in results/test_${FILE_PREFIX}_*.csv; do
    [[ -f "$file" ]] && mv "$file" "$RESULTS_DIR/"
done

echo "Rerun task $SLURM_ARRAY_TASK_ID complete!"
