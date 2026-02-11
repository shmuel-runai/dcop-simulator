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
chmod +x scripts/slurm_dcop_comparison.sh
chmod +x scripts/slurm_dcop_parallel.sh
chmod +x scripts/verify_problems.py
```

### 4. Update Paths in Slurm Script

Use the `--project-dir` option or set the `PROJECT_DIR` environment variable:

```bash
# Option 1: Pass as argument
sbatch scripts/slurm_dcop_comparison.sh --project-dir /path/to/dcop-simulator

# Option 2: Set environment variable
export PROJECT_DIR=/path/to/dcop-simulator
sbatch scripts/slurm_dcop_comparison.sh
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

### Option 1: Submit as a Batch Job (Sequential)

```bash
# Run all algorithms
sbatch scripts/slurm_dcop_comparison.sh

# Run only PMAXSUM
sbatch scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"

# Run PDSA and PMGM
sbatch scripts/slurm_dcop_comparison.sh --algorithms "PDSA PMGM"

# You'll see: "Submitted batch job 12345"
```

### Option 2: Run Interactively (for testing)

```bash
# Request an interactive session first
srun --time=01:00:00 --mem=8G --pty bash

# Then run the script directly
./scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"
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

### 2. Find Your Results Directory

```bash
# List all run directories (most recent first)
ls -lt results/run_*/

# View the configuration of a specific run
cat results/run_<JOBID>_<TIMESTAMP>/config.txt
```

### 3. List Result Files

```bash
# List all CSV files in a run directory
ls -la results/run_<JOBID>_<TIMESTAMP>/*.csv

# Count files
ls results/run_<JOBID>_<TIMESTAMP>/*.csv | wc -l
```

### 4. Verify Problem Consistency

```bash
# Compare problem files to ensure same seeds produced same problems
python3 scripts/verify_problems.py results/run_*/test_*_problems.csv
```

### 5. Quick Results Summary

```bash
# Count results per algorithm in a run
for f in results/run_<JOBID>_<TIMESTAMP>/test_*_results.csv; do
  echo "=== $(basename $f) ==="
  head -5 "$f"
  echo "..."
  wc -l "$f"
done
```

---

## Customizing the Test Matrix

Use the `--algorithms` option to specify which algorithms to run:

```bash
# Run all three algorithms
./scripts/slurm_dcop_comparison.sh --algorithms "PDSA PMGM PMAXSUM"

# Run only PMAXSUM
./scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"

# Run only timeout-based algorithms
./scripts/slurm_dcop_comparison.sh --algorithms "PDSA PMGM"
```

To modify the test parameters (agent counts, timeouts, etc.), edit the script:

```bash
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

Use the parallel script to run configurations simultaneously:

```bash
# Run only PMAXSUM (60 configs = 1 algo × 2 networks × 3 rounds × 10 agents)
sbatch --array=1-60 scripts/slurm_dcop_parallel.sh --algorithms "PMAXSUM"

# Run PDSA and PMGM (120 configs = 2 algos × 2 networks × 3 timeouts × 10 agents)
sbatch --array=1-120 scripts/slurm_dcop_parallel.sh --algorithms "PDSA PMGM"

# Run all algorithms (180 configs)
sbatch --array=1-180 scripts/slurm_dcop_parallel.sh --algorithms "PDSA PMGM PMAXSUM"
```

Configuration counts per algorithm:
- PDSA: 60 configs (1 algo × 2 networks × 3 timeouts × 10 agent counts)
- PMGM: 60 configs (1 algo × 2 networks × 3 timeouts × 10 agent counts)
- PMAXSUM: 60 configs (1 algo × 2 networks × 3 rounds × 10 agent counts)

To run a subset of parallel tasks:

```bash
# Run tasks 1-30 only
sbatch --array=1-30 scripts/slurm_dcop_parallel.sh --algorithms "PMAXSUM"

# Run specific tasks
sbatch --array=1,5,10,20 scripts/slurm_dcop_parallel.sh --algorithms "PMAXSUM"
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
sbatch --mem=32G scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"
```

### Job Timeout
```bash
# Increase time limit
sbatch --time=72:00:00 scripts/slurm_dcop_comparison.sh --algorithms "PMAXSUM"
```

---

## Expected Output

After a successful run, results are organized in a dedicated directory:

```
results/
└── run_<JOBID>_<TIMESTAMP>/
    ├── config.txt                              # Test configuration and parameters
    ├── test_PDSA_RANDOM_t60_n10_results.csv
    ├── test_PDSA_RANDOM_t60_n10_problems.csv   # (first run only)
    ├── test_PDSA_RANDOM_t60_n20_results.csv
    ├── ...
    ├── test_PMGM_SCALE_FREE_t180_n100_results.csv
    ├── test_PMAXSUM_RANDOM_r10_n10_results.csv  # Note: r10 = 10 rounds
    ├── test_PMAXSUM_RANDOM_r20_n50_results.csv
    ├── ...
    └── test_PMAXSUM_SCALE_FREE_r30_n100_results.csv
```

**Directory naming:**
- `run_<JOBID>_<TIMESTAMP>/` - Each run gets its own folder with Slurm job ID and timestamp
- `config.txt` - Contains all test parameters, algorithms, and completion info

**File naming convention:**
- `t60` = timeout 60 seconds (PDSA, PMGM)
- `r10` = 10 rounds (PMAXSUM)

**config.txt contents:**
- Run info: Job ID, timestamp, host
- Algorithms: which were tested, halting types
- Test parameters: networks, agents, domain, costs
- Completion status and duration

Each results file contains:
- Configuration header (algorithm, network, agents, etc.)
- Per-problem results (cost, rounds, runtime)
