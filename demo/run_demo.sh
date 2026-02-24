#!/bin/bash
# ProperTee I/O Demo — orchestration script
# Usage: ./run_demo.sh [script.pt]  (default: io_demo.pt)
set -e

DEMO_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$DEMO_DIR")"
JAR="$PROJECT_DIR/build/libs/propertee-java.jar"
PORT=8099
SCRIPT="${1:-$DEMO_DIR/io_demo.pt}"
SERVER_PID=""

cleanup() {
    if [ -n "$SERVER_PID" ]; then
        # Try graceful shutdown first
        curl -s "http://localhost:$PORT/stop" > /dev/null 2>&1 || true
        sleep 0.5
        # Force kill if still running
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# 1. Build JAR if not present
if [ ! -f "$JAR" ]; then
    echo "Building propertee-java.jar..."
    (cd "$PROJECT_DIR" && ./gradlew jar)
fi

# 2. Compile demo sources
echo "Compiling ShellServer.java..."
javac -source 1.8 -target 1.8 "$DEMO_DIR/ShellServer.java"

echo "Compiling DemoRunner.java..."
javac -source 1.8 -target 1.8 -cp "$JAR" "$DEMO_DIR/DemoRunner.java"

# 3. Start ShellServer in background
echo "Starting ShellServer on port $PORT..."
java -cp "$DEMO_DIR" ShellServer "$PORT" &
SERVER_PID=$!

# 4. Wait for server ready
echo -n "Waiting for server"
for i in $(seq 1 20); do
    if curl -s "http://localhost:$PORT/ping" > /dev/null 2>&1; then
        echo " ready!"
        break
    fi
    if [ $i -eq 20 ]; then
        echo " FAILED (server did not start)"
        exit 1
    fi
    echo -n "."
    sleep 0.3
done

# 5. Run the demo
echo ""
echo "=========================================="
echo "  Running: $SCRIPT"
echo "=========================================="
echo ""
java -cp "$JAR:$DEMO_DIR" DemoRunner -s "http://localhost:$PORT" "$SCRIPT"

echo ""
echo "Done."
