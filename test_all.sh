#!/bin/bash
# Quick test runner for propertee-java
# Usage:
#   ./test_all.sh              # test default JAR (propertee-java.jar)
#   ./test_all.sh java7        # test Java 7 JAR (propertee-java-java7.jar)
#   ./test_all.sh java8        # test Java 8 JAR (propertee-java-java8.jar)
#   ./test_all.sh all          # test both Java 7 and Java 8 JARs
#   ./test_all.sh /path/to.jar # test a specific JAR file
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_DIR="$SCRIPT_DIR/src/test/resources/tests"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

run_tests() {
    local JAR="$1"
    local LABEL="$2"
    local PASS=0
    local FAIL=0

    if [ ! -f "$JAR" ]; then
        printf "${RED}ERROR${NC} JAR not found: %s\n" "$JAR"
        return 1
    fi

    printf "${YELLOW}=== Testing: %s ===${NC}\n" "$LABEL"

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
    printf "%s Results: ${PASS} passed, ${FAIL} failed\n" "$LABEL"
    [ $FAIL -gt 0 ] && return 1
    return 0
}

# Resolve which JAR(s) to test
TARGET="${1:-default}"
EXITCODE=0

case "$TARGET" in
    java7)
        run_tests "$SCRIPT_DIR/build/libs/propertee-java-java7.jar" "Java 7" || EXITCODE=1
        ;;
    java8)
        run_tests "$SCRIPT_DIR/build/libs/propertee-java-java8.jar" "Java 8" || EXITCODE=1
        ;;
    all)
        run_tests "$SCRIPT_DIR/build/libs/propertee-java-java7.jar" "Java 7" || EXITCODE=1
        echo ""
        run_tests "$SCRIPT_DIR/build/libs/propertee-java-java8.jar" "Java 8" || EXITCODE=1
        ;;
    default)
        run_tests "$SCRIPT_DIR/build/libs/propertee-java-java8.jar" "Java 8" || EXITCODE=1
        ;;
    *)
        # Treat as a direct path to a JAR
        run_tests "$TARGET" "$TARGET" || EXITCODE=1
        ;;
esac

exit $EXITCODE
