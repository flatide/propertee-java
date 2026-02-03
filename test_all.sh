#!/bin/bash
# Quick test runner for propertee-java
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/build/libs/propertee-java.jar"
TEST_DIR="$SCRIPT_DIR/src/test/resources/tests"
PASS=0
FAIL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

for pt_file in "$TEST_DIR"/*.pt; do
    [ -f "$pt_file" ] || continue
    base="${pt_file%.pt}"
    expected_file="${base}.expected"
    test_name="$(basename "$base")"

    [ -f "$expected_file" ] || continue

    case "$test_name" in
        34_builtin_properties)
            actual=$(java -jar "$JAR" -p '{"width":100,"height":200,"name":"test"}' "$pt_file" 2>&1)
            ;;
        *)
            actual=$(java -jar "$JAR" "$pt_file" 2>&1)
            ;;
    esac
    expected=$(cat "$expected_file")

    if [ "$actual" = "$expected" ]; then
        printf "${GREEN}PASS${NC} %s\n" "$test_name"
        PASS=$((PASS + 1))
    else
        printf "${RED}FAIL${NC} %s\n" "$test_name"
        echo "  --- expected ---"
        echo "$expected" | head -10 | sed 's/^/  /'
        echo "  --- actual ---"
        echo "$actual" | head -10 | sed 's/^/  /'
        echo ""
        FAIL=$((FAIL + 1))
    fi
done

echo ""
echo "Results: ${PASS} passed, ${FAIL} failed"
[ $FAIL -gt 0 ] && exit 1
exit 0
