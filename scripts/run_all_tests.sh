#!/bin/bash
# Run all DCOP performance tests
#
# Usage:
#   ./scripts/run_all_tests.sh              # Run all tests locally
#   ./scripts/run_all_tests.sh --slurm      # Submit to Slurm
#
# Tests:
#   1. Agent count test:    agents 10-100, domain=10
#   2. Domain size test:    domain 5-25, agents=30
#   3. Network density test: density 0.2-1.0, agents=30, domain=10

ALGORITHMS="PDSA PMGM PMAXSUM"

if [[ "$1" == "--slurm" ]]; then
    echo "Submitting tests to Slurm..."
    
    sbatch scripts/slurm_test_agents.sh "$ALGORITHMS"
    sbatch scripts/slurm_test_domain.sh "$ALGORITHMS"
    sbatch scripts/slurm_test_density.sh "$ALGORITHMS"
    
    echo ""
    echo "Submitted 3 jobs. Check status with: squeue -u \$USER"
else
    echo "Running tests locally (this will take a long time)..."
    echo ""
    
    echo "=== Test 1: Agent Count ==="
    ./scripts/slurm_test_agents.sh "$ALGORITHMS"
    
    echo ""
    echo "=== Test 2: Domain Size ==="
    ./scripts/slurm_test_domain.sh "$ALGORITHMS"
    
    echo ""
    echo "=== Test 3: Network Density ==="
    ./scripts/slurm_test_density.sh "$ALGORITHMS"
    
    echo ""
    echo "All tests complete!"
fi
