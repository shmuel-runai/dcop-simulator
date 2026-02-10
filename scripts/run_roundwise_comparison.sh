#!/bin/bash
# Round-by-round comparison of MAXSUM vs P-MAXSUM
#
# This script runs both algorithms with the same seed for each round value,
# then compares costs to verify they produce identical results.
#
# Usage: ./scripts/run_roundwise_comparison.sh

set -e

# Configuration
JAVA_CMD="java -Djava.awt.headless=true -Xmx8g -cp binaries/bin:binaries/jdom.jar"
MAIN_CLASS="sinalgo.runtime.Main"
PROJECT="-project dcopProject -batch"

# Test parameters (uses Config.xml defaults for problemSeed=1000)
NUM_PROBLEMS=15
NUM_AGENTS=10
DOMAIN_SIZE=5
NETWORK_DENSITY=0.3
MIN_ROUND=1
MAX_ROUND=20

# Create results directory with timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="results/roundwise_comparison_${TIMESTAMP}"
mkdir -p "$RESULTS_DIR"

echo "=============================================="
echo "Round-by-Round MAXSUM vs PMAXSUM Comparison"
echo "=============================================="
echo ""
echo "Configuration:"
echo "  Problems per round: $NUM_PROBLEMS"
echo "  Agents: $NUM_AGENTS"
echo "  Domain size: $DOMAIN_SIZE"
echo "  Network density: $NETWORK_DENSITY"
echo "  Problem seed: 1000 (from Config.xml)"
echo "  Round range: $MIN_ROUND to $MAX_ROUND"
echo ""
echo "Results will be saved to: $RESULTS_DIR"
echo ""

# Track results
PASS_COUNT=0
FAIL_COUNT=0
FAIL_ROUNDS=""

START_TIME=$(date +%s)

for ROUND in $(seq $MIN_ROUND $MAX_ROUND); do
    echo "--- Round $ROUND ---"
    
    # Run MAXSUM (uses default problemSeed=1000 from Config.xml)
    MAXSUM_OUTPUT=$($JAVA_CMD $MAIN_CLASS $PROJECT \
        -overwrite DCOPTest/algorithm=MAXSUM \
        -overwrite DCOPTest/lastRound=$ROUND \
        -overwrite DCOPTest/numIterations=$NUM_PROBLEMS \
        -overwrite DCOPTest/numAgents=$NUM_AGENTS \
        -overwrite DCOPTest/domainSize=$DOMAIN_SIZE \
        -overwrite DCOPTest/networkDensity=$NETWORK_DENSITY \
        2>&1)
    
    # Extract costs for each iteration
    MAXSUM_COSTS=$(echo "$MAXSUM_OUTPUT" | grep "Iteration.*Cost=" | sed 's/.*Cost=\([0-9]*\).*/\1/' | tr '\n' ',')
    MAXSUM_AVG=$(echo "$MAXSUM_OUTPUT" | grep "Average Cost:" | sed 's/.*: //')
    
    # Move MAXSUM result file
    for CSV in logs/dcop_results_MAXSUM_*.csv; do
        if [[ -f "$CSV" ]]; then
            mv "$CSV" "$RESULTS_DIR/maxsum_round${ROUND}.csv"
        fi
    done
    
    # Run PMAXSUM (uses same default problemSeed=1000 from Config.xml)
    PMAXSUM_OUTPUT=$($JAVA_CMD $MAIN_CLASS $PROJECT \
        -overwrite DCOPTest/algorithm=PMAXSUM \
        -overwrite DCOPTest/lastRound=$ROUND \
        -overwrite DCOPTest/numIterations=$NUM_PROBLEMS \
        -overwrite DCOPTest/numAgents=$NUM_AGENTS \
        -overwrite DCOPTest/domainSize=$DOMAIN_SIZE \
        -overwrite DCOPTest/networkDensity=$NETWORK_DENSITY \
        2>&1)
    
    # Extract costs for each iteration
    PMAXSUM_COSTS=$(echo "$PMAXSUM_OUTPUT" | grep "Iteration.*Cost=" | sed 's/.*Cost=\([0-9]*\).*/\1/' | tr '\n' ',')
    PMAXSUM_AVG=$(echo "$PMAXSUM_OUTPUT" | grep "Average Cost:" | sed 's/.*: //')
    
    # Move PMAXSUM result file
    for CSV in logs/dcop_results_PMAXSUM_*.csv; do
        if [[ -f "$CSV" ]]; then
            mv "$CSV" "$RESULTS_DIR/pmaxsum_round${ROUND}.csv"
        fi
    done
    
    # Compare results
    if [[ "$MAXSUM_COSTS" == "$PMAXSUM_COSTS" ]]; then
        echo "  PASS: MAXSUM avg=$MAXSUM_AVG, PMAXSUM avg=$PMAXSUM_AVG (costs match)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  FAIL: Costs differ!"
        echo "    MAXSUM:  $MAXSUM_COSTS (avg=$MAXSUM_AVG)"
        echo "    PMAXSUM: $PMAXSUM_COSTS (avg=$PMAXSUM_AVG)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAIL_ROUNDS="$FAIL_ROUNDS $ROUND"
    fi
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=============================================="
echo "Comparison Complete"
echo "=============================================="
echo "Duration: ${DURATION} seconds"
echo "Passed: $PASS_COUNT / $((PASS_COUNT + FAIL_COUNT))"

if [[ $FAIL_COUNT -gt 0 ]]; then
    echo "FAILED rounds:$FAIL_ROUNDS"
    echo ""
    echo "RESULT: FAILURE - some rounds produced different results"
    exit 1
else
    echo ""
    echo "RESULT: SUCCESS - all rounds produced identical results"
fi

# Create summary file
SUMMARY_FILE="$RESULTS_DIR/comparison_summary.txt"
{
    echo "Round-by-Round Comparison Summary"
    echo "================================="
    echo ""
    echo "Date: $(date)"
    echo "Duration: ${DURATION} seconds"
    echo ""
    echo "Configuration:"
    echo "  numProblems: $NUM_PROBLEMS"
    echo "  numAgents: $NUM_AGENTS"
    echo "  domainSize: $DOMAIN_SIZE"
    echo "  networkDensity: $NETWORK_DENSITY"
    echo "  problemSeed: 1000 (from Config.xml)"
    echo "  roundRange: $MIN_ROUND to $MAX_ROUND"
    echo ""
    echo "Results:"
    echo "  Passed: $PASS_COUNT"
    echo "  Failed: $FAIL_COUNT"
    if [[ $FAIL_COUNT -gt 0 ]]; then
        echo "  Failed rounds:$FAIL_ROUNDS"
    fi
} > "$SUMMARY_FILE"

echo ""
echo "Summary saved to: $SUMMARY_FILE"
echo "Results saved to: $RESULTS_DIR"
