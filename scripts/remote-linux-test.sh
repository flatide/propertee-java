#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  remote-linux-test.sh <user@host> [remote-dir]

Environment:
  REMOTE_TEST_PROFILE Default: linux-regression
                    - linux-regression: ScriptTest 79_task_cancel + TaskEngineTest
                    - all-core: full ScriptTest + TaskEngineTest
  REMOTE_TEST_CMD   Remote command to execute after upload.
                    Overrides REMOTE_TEST_PROFILE when set.
  REMOTE_SSH_KEY    Identity file for ssh/scp.
  REMOTE_SSH_PORT   SSH port. Default: 22
  REMOTE_SSH_OPTS   Extra options passed to ssh/scp after the standard args.
  KEEP_REMOTE       If set to 1, keep the uploaded remote work directory.

Examples:
  scripts/remote-linux-test.sh user@linux-host
  REMOTE_TEST_PROFILE=all-core \
    scripts/remote-linux-test.sh user@linux-host /tmp/propertee-ci
  REMOTE_TEST_CMD='./gradlew --no-daemon :propertee-core:test --tests com.propertee.tests.ScriptTest.testScript[79_task_cancel]' \
    scripts/remote-linux-test.sh user@linux-host /tmp/propertee-ci
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

REMOTE_HOST="${1:-}"
if [[ -z "$REMOTE_HOST" ]]; then
    usage >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REMOTE_DIR="${2:-/tmp/propertee-java-remote-test}"
REMOTE_SSH_KEY="${REMOTE_SSH_KEY:-}"
REMOTE_SSH_PORT="${REMOTE_SSH_PORT:-22}"
REMOTE_SSH_OPTS="${REMOTE_SSH_OPTS:-}"
REMOTE_TEST_PROFILE="${REMOTE_TEST_PROFILE:-linux-regression}"

default_test_command() {
    case "$REMOTE_TEST_PROFILE" in
        linux-regression)
            printf '%s' "./gradlew --no-daemon :propertee-core:test --tests com.propertee.tests.ScriptTest.testScript[79_task_cancel] --tests com.propertee.tests.TaskEngineTest"
            ;;
        all-core)
            printf '%s' "./gradlew --no-daemon :propertee-core:test --tests com.propertee.tests.ScriptTest --tests com.propertee.tests.TaskEngineTest"
            ;;
        *)
            echo "Unknown REMOTE_TEST_PROFILE: $REMOTE_TEST_PROFILE" >&2
            exit 1
            ;;
    esac
}

REMOTE_TEST_CMD="${REMOTE_TEST_CMD:-$(default_test_command)}"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="$REPO_ROOT/build/remote-linux/$STAMP"
LOG_FILE="$ARTIFACT_DIR/remote-test.log"

mkdir -p "$ARTIFACT_DIR"

echo "[remote-linux-test] remote host: $REMOTE_HOST"
echo "[remote-linux-test] remote dir : $REMOTE_DIR"
echo "[remote-linux-test] artifacts  : $ARTIFACT_DIR"
echo "[remote-linux-test] profile    : $REMOTE_TEST_PROFILE"
echo "[remote-linux-test] command    : $REMOTE_TEST_CMD"
echo "[remote-linux-test] ssh port   : $REMOTE_SSH_PORT"

SSH_ARGS=(-o BatchMode=yes -o StrictHostKeyChecking=no -p "$REMOTE_SSH_PORT")
if [[ -n "$REMOTE_SSH_KEY" ]]; then
    SSH_ARGS+=(-i "$REMOTE_SSH_KEY")
fi
if [[ -n "$REMOTE_SSH_OPTS" ]]; then
    # shellcheck disable=SC2206
    EXTRA_SSH_ARGS=($REMOTE_SSH_OPTS)
    SSH_ARGS+=("${EXTRA_SSH_ARGS[@]}")
fi

upload_repo() {
    tar \
        --exclude='.git' \
        --exclude='.gradle' \
        --exclude='build' \
        --exclude='*/build' \
        -C "$REPO_ROOT" \
        -cf - . | \
        ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "rm -rf '$REMOTE_DIR' && mkdir -p '$REMOTE_DIR' && tar -xf - -C '$REMOTE_DIR'"
}

run_remote_tests() {
    ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "cd '$REMOTE_DIR' && $REMOTE_TEST_CMD"
}

fetch_artifacts() {
    ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "
        cd '$REMOTE_DIR' || exit 0
        tar -cf - --ignore-failed-read \
            propertee-core/build/test-results \
            propertee-core/build/reports/tests \
            propertee-teebox/build/test-results \
            propertee-teebox/build/reports/tests \
            build/reports/problems 2>/dev/null || true
    " | tar -xf - -C "$ARTIFACT_DIR" 2>/dev/null || true
}

cleanup_remote() {
    if [[ "${KEEP_REMOTE:-0}" == "1" ]]; then
        echo "[remote-linux-test] keeping remote dir: $REMOTE_DIR"
        return
    fi
    ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "rm -rf '$REMOTE_DIR'" >/dev/null 2>&1 || true
}

upload_repo

set +e
run_remote_tests 2>&1 | tee "$LOG_FILE"
REMOTE_STATUS=${PIPESTATUS[0]}
set -e

fetch_artifacts
cleanup_remote

echo "[remote-linux-test] remote exit code: $REMOTE_STATUS"
echo "[remote-linux-test] log file: $LOG_FILE"
exit "$REMOTE_STATUS"
