#!/bin/bash
#SBATCH --job-name=pmx_agents
#SBATCH --output=slurm_logs/pmx_agents_%A_%a.out
#SBATCH --error=slurm_logs/pmx_agents_%A_%a.err
#SBATCH --time=10:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --array=1-100

# PMAXSUM-only rerun - Varying Agent Count
# Total tasks: 2 networks × 5 timeouts × 10 agent counts = 100

set -e

PROJECT_DIR="${PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR"
mkdir -p results logs slurm_logs

module load java/1.8.0.371

NETWORK_TYPES=(RANDOM SCALE_FREE)
TIMEOUTS=(60 120 180 240 300)
AGENT_COUNTS=(10 20 30 40 50 60 70 80 90 100)

DOMAIN_SIZE=10
MIN_COST=0
MAX_COST=10
NUM_PROBLEMS=50
PROBLEM_SEED=1000
RANDOM_DENSITY=0.4
SCALEFREE_INIT=5
SCALEFREE_ADD=4

NUM_NETS=${#NETWORK_TYPES[@]}
NUM_TIMEOUTS=${#TIMEOUTS[@]}
NUM_AGENTS=${#AGENT_COUNTS[@]}

TASK_ID=${SLURM_ARRAY_TASK_ID:-1}
TASK_IDX=$((TASK_ID - 1))

AGENT_IDX=$((TASK_IDX % NUM_AGENTS))
TASK_IDX=$((TASK_IDX / NUM_AGENTS))

TIMEOUT_IDX=$((TASK_IDX % NUM_TIMEOUTS))
NET_IDX=$((TASK_IDX / NUM_TIMEOUTS))

NET_TYPE=${NETWORK_TYPES[$NET_IDX]}
TIMEOUT=${TIMEOUTS[$TIMEOUT_IDX]}
NUM_AGENTS_VAL=${AGENT_COUNTS[$AGENT_IDX]}

ARRAY_JOB_ID="${SLURM_ARRAY_JOB_ID:-local}"
RUN_DIR="results/pmx_agents_${ARRAY_JOB_ID}"
mkdir -p "$RUN_DIR"

FILE_PREFIX="PMAXSUM_${NET_TYPE}_t${TIMEOUT}_n${NUM_AGENTS_VAL}"
echo "Task $SLURM_ARRAY_TASK_ID: PMAXSUM / $NET_TYPE / ${TIMEOUT}s / ${NUM_AGENTS_VAL} agents"

CMD="./scripts/run_algorithm_test.sh"
CMD="$CMD --algorithm PMAXSUM"
CMD="$CMD --network-type $NET_TYPE"
CMD="$CMD --num-agents $NUM_AGENTS_VAL"
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

for file in results/test_${FILE_PREFIX}_*.csv; do
    [[ -f "$file" ]] && mv "$file" "$RUN_DIR/"
done

echo "Task $SLURM_ARRAY_TASK_ID complete!"
