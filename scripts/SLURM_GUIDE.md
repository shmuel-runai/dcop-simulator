# DCOP Simulator - Slurm Execution Guide

## Prerequisites

### 1. Clone and Build the Project

```bash
# Clone the repository
git clone <your-repo-url> dcop-simulator
cd dcop-simulator

# Build the project
ant compile
```

### 2. Verify Java Version

```bash
java -version
# Should be Java 8 or higher
```

### 3. Make Scripts Executable

```bash
chmod +x scripts/run_algorithm_test.sh
chmod +x scripts/slurm_pdsa_pmgm_comparison.sh
chmod +x scripts/verify_problems.py
```

### 4. Update Paths in Slurm Script

Edit `scripts/slurm_pdsa_pmgm_comparison.sh` and update the project directory:

```bash
# Change this line to match your environment:
cd /path/to/your/dcop-simulator
```

### 5. Create Required Directories

```bash
mkdir -p results logs slurm_logs
```

---

## Pre-Run Checklist

Before submitting to Slurm, verify everything works locally:

```bash
# Quick test with 1 problem, short timeout
./scripts/run_algorithm_test.sh \
  --algorithm PDSA \
  --network-type RANDOM \
  --num-agents 10 \
  --domain-size 5 \
  --timeout 10 \
  --num-problems 1 \
  --output-prefix local_test

# Check the output
cat results/test_local_test_results.csv
```

If this works, you're ready for Slurm.

---

## Running with Slurm

### Option 1: Submit as a Batch Job

```bash
# Submit the job
sbatch scripts/slurm_pdsa_pmgm_comparison.sh

# You'll see: "Submitted batch job 12345"
```

### Option 2: Run Interactively (for testing)

```bash
# Request an interactive session first
srun --time=01:00:00 --mem=8G --pty bash

# Then run the script directly
./scripts/slurm_pdsa_pmgm_comparison.sh
```

### Option 3: Run a Single Configuration

```bash
# Submit a single test as a job
sbatch --wrap="cd /path/to/dcop-simulator && ./scripts/run_algorithm_test.sh \
  --algorithm PDSA \
  --network-type RANDOM \
  --num-agents 50 \
  --timeout 120 \
  --num-problems 50 \
  --output-prefix pdsa_n50_t120"
```

---

## Monitoring Jobs

### Check Job Status

```bash
# See your jobs
squeue -u $USER

# See all details
squeue -u $USER -l

# See job info
scontrol show job <job_id>
```

### Watch Output in Real-Time

```bash
# Tail the output file
tail -f slurm_logs/dcop_<job_id>.out

# Or check error file
tail -f slurm_logs/dcop_<job_id>.err
```

### Cancel a Job

```bash
scancel <job_id>

# Cancel all your jobs
scancel -u $USER
```

---

## After Completion

### 1. Check for Errors

```bash
# Look for errors in the log
grep -i "error\|exception\|failed" slurm_logs/dcop_*.err
grep -i "error\|exception\|failed" slurm_logs/dcop_*.out
```

### 2. List Result Files

```bash
ls -la results/test_comparison_*.csv
```

### 3. Verify Problem Consistency

```bash
# Compare problem files to ensure same seeds produced same problems
python3 scripts/verify_problems.py results/test_*_problems.csv
```

### 4. Quick Results Summary

```bash
# Count results per algorithm
for f in results/test_*_results.csv; do
  echo "=== $f ==="
  head -5 "$f"
  echo "..."
  wc -l "$f"
done
```

---

## Customizing the Test Matrix

Edit `scripts/slurm_pdsa_pmgm_comparison.sh` to modify:

```bash
# Algorithms to test
# Timeout-based algorithms (use --timeout parameter)
TIMEOUT_ALGORITHMS="PDSA PMGM"

# Round-based algorithms (use --last-round parameter)
ROUND_ALGORITHMS="PMAXSUM"

# Network topologies
NETWORK_TYPES="RANDOM SCALE_FREE"

# Timeouts in seconds (for PDSA, PMGM)
TIMEOUTS="60 120 180"

# Rounds (for PMAXSUM)
ROUNDS="10 20 30"

# Agent counts
AGENT_COUNTS="10 20 30 40 50 60 70 80 90 100"

# Problems per configuration
NUM_PROBLEMS=50
```

### Algorithm Types

| Algorithm | Type | Halting | Notes |
|-----------|------|---------|-------|
| PDSA | Timeout-based | `--timeout <seconds>` | Privacy-preserving DSA |
| PMGM | Timeout-based | `--timeout <seconds>` | Privacy-preserving MGM |
| PMAXSUM | Round-based | `--last-round <n>` | Privacy-preserving Max-Sum |

---

## Slurm Resource Settings

Adjust these in the script header if needed:

```bash
#SBATCH --time=48:00:00      # Max runtime (HH:MM:SS)
#SBATCH --mem=16G            # Memory per job
#SBATCH --cpus-per-task=1    # CPUs per job
```

For larger agent counts (80-100), you may need more memory:

```bash
#SBATCH --mem=32G
```

---

## Parallel Execution (Advanced)

Use the parallel script to run all 180 configurations simultaneously:

```bash
# Submit all configurations as a job array
sbatch scripts/slurm_pdsa_pmgm_parallel.sh

# This creates 180 parallel jobs:
# - Tasks 1-120: PDSA and PMGM (timeout-based)
# - Tasks 121-180: PMAXSUM (round-based)
```

The parallel script (`slurm_pdsa_pmgm_parallel.sh`) automatically maps each array task ID to a unique configuration:

```bash
#SBATCH --array=1-180        # 180 configurations total
```

To run a subset of tasks:

```bash
# Only PDSA and PMGM (tasks 1-120)
sbatch --array=1-120 scripts/slurm_pdsa_pmgm_parallel.sh

# Only PMAXSUM (tasks 121-180)
sbatch --array=121-180 scripts/slurm_pdsa_pmgm_parallel.sh

# Only specific agent counts (e.g., 10, 50, 100 agents)
sbatch --array=1,5,10,61,65,70,121,125,130 scripts/slurm_pdsa_pmgm_parallel.sh
```

---

## Troubleshooting

### "Command not found: java"
```bash
# Load Java module (cluster-specific)
module load java/1.8
# or
module load openjdk/11
```

### "Permission denied"
```bash
chmod +x scripts/*.sh
```

### "No such file or directory: binaries/bin"
```bash
# Rebuild the project
ant clean compile
```

### Out of Memory
```bash
# Increase memory in the script or via sbatch
sbatch --mem=32G scripts/slurm_pdsa_pmgm_comparison.sh
```

### Job Timeout
```bash
# Increase time limit
sbatch --time=72:00:00 scripts/slurm_pdsa_pmgm_comparison.sh
```

---

## Expected Output

After a successful run, you'll have:

```
results/
├── test_comparison_YYYYMMDD_HHMMSS_PDSA_RANDOM_t60_n10_results.csv
├── test_comparison_YYYYMMDD_HHMMSS_PDSA_RANDOM_t60_n10_problems.csv  # (first run only)
├── test_comparison_YYYYMMDD_HHMMSS_PDSA_RANDOM_t60_n20_results.csv
├── ...
├── test_comparison_YYYYMMDD_HHMMSS_PMGM_SCALE_FREE_t180_n100_results.csv
├── test_comparison_YYYYMMDD_HHMMSS_PMAXSUM_RANDOM_r10_n10_results.csv  # Note: r10 = 10 rounds
├── test_comparison_YYYYMMDD_HHMMSS_PMAXSUM_RANDOM_r20_n50_results.csv
├── ...
└── test_comparison_YYYYMMDD_HHMMSS_PMAXSUM_SCALE_FREE_r30_n100_results.csv
```

**Naming convention:**
- `t60` = timeout 60 seconds (PDSA, PMGM)
- `r10` = 10 rounds (PMAXSUM)

Each results file contains:
- Configuration header (algorithm, network, agents, etc.)
- Per-problem results (cost, rounds, runtime)
