#!/bin/bash
#SBATCH --job-name=pmx_density
#SBATCH --output=slurm_logs/pmx_density_%A_%a.out
#SBATCH --error=slurm_logs/pmx_density_%A_%a.err
#SBATCH --time=48:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --array=1-25

# PMAXSUM-only rerun - Varying Network Density
# Total tasks: 5 timeouts × 5 densities = 25

set -e

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

module load java/1.8.0.371

TIMEOUTS=(60 120 180 240 300)
DENSITIES=(0.2 0.4 0.6 0.8 1.0)

NUM_AGENTS=30
DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000

NUM_TIMEOUTS=${#TIMEOUTS[@]}
NUM_DENSITIES=${#DENSITIES[@]}

TASK_ID=${SLURM_ARRAY_TASK_ID:-1}
TASK_IDX=$((TASK_ID - 1))

DENSITY_IDX=$((TASK_IDX % NUM_DENSITIES))
TIMEOUT_IDX=$((TASK_IDX / NUM_DENSITIES))

TIMEOUT=${TIMEOUTS[$TIMEOUT_IDX]}
DENSITY=${DENSITIES[$DENSITY_IDX]}

ARRAY_JOB_ID="${SLURM_ARRAY_JOB_ID:-local}"
RUN_DIR="results/pmx_density_${ARRAY_JOB_ID}"
mkdir -p "$RUN_DIR"

DENSITY_LABEL=$(echo $DENSITY | tr -d '.')
FILE_PREFIX="PMAXSUM_RANDOM_t${TIMEOUT}_nd${DENSITY_LABEL}"
echo "Task $SLURM_ARRAY_TASK_ID: PMAXSUM / RANDOM / ${TIMEOUT}s / density=${DENSITY}"

CMD="./scripts/run_algorithm_test.sh"
CMD="$CMD --algorithm PMAXSUM"
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

for file in results/test_${FILE_PREFIX}_*.csv; do
    [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
done

echo "Task $SLURM_ARRAY_TASK_ID complete!"
