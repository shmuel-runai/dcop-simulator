#!/bin/bash
# Script to run Sinalgo GUI without IDE interference

cd /Users/sgoldklang/java/sinalgo-0.75.3-regularRelease

# Kill any Java language servers
pkill -9 -f "redhat.java" 2>/dev/null

# Clean and compile
echo "Cleaning..."
chmod -R u+w binaries/bin 2>/dev/null
rm -rf binaries/bin
mkdir -p binaries/bin

echo "Compiling..."
ant compile 2>&1 | grep -E "BUILD|error:"

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
    
    # Make read-only
    chmod -R a-w binaries/bin
    
    # Run immediately
    echo "Starting GUI..."
    /opt/homebrew/opt/java/bin/java -Xmx8g -cp binaries/bin:binaries/jdom.jar sinalgo.runtime.Main -project dcopProject
else
    echo "✗ Compilation failed"
    exit 1
fi










