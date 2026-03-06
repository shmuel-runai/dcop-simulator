#!/bin/bash
# Run PMAXSUM RANDOM n=100 timeout=300s, 5 problems, on node cn19 only.
# To test whether t300 completes at least 1 round on the same node that gave t240=1 round.
#SBATCH --job-name=cn19_t300
#SBATCH --output=slurm_logs/cn19_t300_n100_%j.out
#SBATCH --error=slurm_logs/cn19_t300_n100_%j.err
#SBATCH --time=01:00:00
#SBATCH --mem=16G
#SBATCH --cpus-per-task=16
#SBATCH --nodelist=cn19

module load java/1.8.0.371

cd ~/work/dcop-simulator || exit 1

echo "=== PMAXSUM RANDOM n100 t300, 5 problems, node cn19 ==="
echo "Node: $(hostname)"

JAVA_CMD="java -Djava.awt.headless=true -Xmx12g" \
  bash scripts/run_algorithm_test.sh \
    --algorithm PMAXSUM \
    --network-type RANDOM \
    --num-agents 100 \
    --domain-size 10 \
    --timeout 300 \
    --num-problems 5 \
    --min-cost 0 \
    --max-cost 10 \
    --network-density 0.4 \
    --problem-seed 1000 \
    --output-prefix cn19_t300_n100

echo "=== DONE ==="
