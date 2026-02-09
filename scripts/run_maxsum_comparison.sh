#!/bin/bash
# Run MAXSUM vs P-MAXSUM comparison tests
#
# This script runs both algorithms across 30 different lastRound values
# with 15 problems each, then organizes the results for comparison.
#
# Usage: ./scripts/run_maxsum_comparison.sh [--quick]
#   --quick: Run a quick smoke test (2 iterations, rounds 1-3 only)

set -e

# Configuration
JAVA_CMD="java -Xmx8g -cp binaries/bin:binaries/jdom.jar"
MAIN_CLASS="sinalgo.runtime.Main"
PROJECT="-project dcopProject -batch"

# Check for quick mode
QUICK_MODE=false
if [[ "$1" == "--quick" ]]; then
    QUICK_MODE=true
    echo "=== QUICK MODE: Running smoke test ==="
fi

# Create results directory with timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="results/maxsum_comparison_${TIMESTAMP}"
mkdir -p "$RESULTS_DIR"
mkdir -p logs

echo "Results will be saved to: $RESULTS_DIR"
echo ""

# Fixed configuration parameters
NUM_ITERATIONS=15
NUM_AGENTS=10
DOMAIN_SIZE=5
MIN_COST=0
MAX_COST=10
NETWORK_DENSITY=0.3

# Adjust for quick mode
if $QUICK_MODE; then
    NUM_ITERATIONS=2
    MAX_ROUNDS=3
    ALGORITHMS="MAXSUM"
else
    MAX_ROUNDS=30
    ALGORITHMS="MAXSUM PMAXSUM"
fi

# Track timing
START_TIME=$(date +%s)

# Run tests
for ALGO in $ALGORITHMS; do
    echo "=========================================="
    echo "Algorithm: $ALGO"
    echo "=========================================="
    
    for ROUND in $(seq 1 $MAX_ROUNDS); do
        echo ""
        echo "--- $ALGO: lastRound=$ROUND ---"
        
        # Run simulation
        $JAVA_CMD $MAIN_CLASS $PROJECT \
            -overwrite DCOPTest/algorithm=$ALGO \
            -overwrite DCOPTest/lastRound=$ROUND \
            -overwrite DCOPTest/numIterations=$NUM_ITERATIONS \
            -overwrite DCOPTest/numAgents=$NUM_AGENTS \
            -overwrite DCOPTest/domainSize=$DOMAIN_SIZE \
            -overwrite DCOPTest/minCost=$MIN_COST \
            -overwrite DCOPTest/maxCost=$MAX_COST \
            -overwrite DCOPTest/networkDensity=$NETWORK_DENSITY \
            2>&1 | tail -20
        
        # Move results to organized folder with round number in filename
        for CSV in logs/dcop_results_${ALGO}_*.csv; do
            if [[ -f "$CSV" ]]; then
                BASENAME=$(basename "$CSV" .csv)
                mv "$CSV" "$RESULTS_DIR/${BASENAME}_round${ROUND}.csv"
                echo "  Saved: ${BASENAME}_round${ROUND}.csv"
            fi
        done
    done
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Test Complete!"
echo "=========================================="
echo "Duration: ${DURATION} seconds"
echo "Results saved to: $RESULTS_DIR"
echo ""

# List results
echo "Generated files:"
ls -la "$RESULTS_DIR"

# Create a summary file
echo ""
echo "Creating summary..."
SUMMARY_FILE="$RESULTS_DIR/test_summary.txt"
{
    echo "MaxSum Comparison Test Summary"
    echo "=============================="
    echo ""
    echo "Date: $(date)"
    echo "Duration: ${DURATION} seconds"
    echo ""
    echo "Configuration:"
    echo "  numIterations: $NUM_ITERATIONS"
    echo "  numAgents: $NUM_AGENTS"
    echo "  domainSize: $DOMAIN_SIZE"
    echo "  minCost: $MIN_COST"
    echo "  maxCost: $MAX_COST"
    echo "  networkDensity: $NETWORK_DENSITY"
    echo "  lastRound range: 1 to $MAX_ROUNDS"
    echo ""
    echo "Algorithms tested: $ALGORITHMS"
    echo ""
    echo "Files generated:"
    ls -1 "$RESULTS_DIR"/*.csv 2>/dev/null || echo "  (none)"
} > "$SUMMARY_FILE"

echo "Summary saved to: $SUMMARY_FILE"
