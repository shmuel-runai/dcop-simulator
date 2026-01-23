#!/bin/bash

# MPC Test Suite Launcher
# Runs the MPC testing simulation with 10 nodes

echo "=========================================="
echo "  MPC Test Suite - Distributed Protocols"
echo "=========================================="
echo ""

# Change to project directory
cd "$(dirname "$0")"

# Run Sinalgo in batch mode with mpcTest project
# Topology is created in CustomGlobal.java, so no -gen needed
# Using 100M rounds to accommodate extensive array tests
/opt/homebrew/opt/java/bin/java -Xmx500m -cp binaries/bin:binaries/jdom.jar \
    sinalgo.Run \
    -project mpcTest \
    -batch \
    -rounds 100000000

echo ""
echo "Test suite completed!"


