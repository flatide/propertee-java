#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  remote-teebox-smoke.sh <user@host> [remote-dir]

Environment:
  REMOTE_SSH_KEY   Identity file for ssh/scp.
  REMOTE_SSH_PORT  SSH port. Default: 22
  REMOTE_SSH_OPTS  Extra options passed to ssh/scp after the standard args.
  TEEBOX_PORT      TeeBox port on remote host. Default: 18080
  KEEP_REMOTE      If set to 1, keep the uploaded remote work directory.

What it does:
  1. Builds teeBoxZip locally
  2. Copies the zip to the remote Linux server
  3. Unpacks it in a temp directory
  4. Starts TeeBox on 127.0.0.1
  5. Verifies /admin responds
  6. Stops the server and collects the remote log
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
REMOTE_DIR="${2:-/tmp/propertee-teebox-smoke}"
REMOTE_SSH_KEY="${REMOTE_SSH_KEY:-}"
REMOTE_SSH_PORT="${REMOTE_SSH_PORT:-22}"
REMOTE_SSH_OPTS="${REMOTE_SSH_OPTS:-}"
TEEBOX_PORT="${TEEBOX_PORT:-18080}"
DIST_ZIP="$REPO_ROOT/build/distributions/propertee-teebox-dist.zip"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="$REPO_ROOT/build/remote-linux/$STAMP-teebox"
LOG_FILE="$ARTIFACT_DIR/teebox-smoke.log"

mkdir -p "$ARTIFACT_DIR"

SSH_ARGS=(-o BatchMode=yes -o StrictHostKeyChecking=no -p "$REMOTE_SSH_PORT")
SCP_ARGS=(-o BatchMode=yes -o StrictHostKeyChecking=no -P "$REMOTE_SSH_PORT")
if [[ -n "$REMOTE_SSH_KEY" ]]; then
    SSH_ARGS+=(-i "$REMOTE_SSH_KEY")
    SCP_ARGS+=(-i "$REMOTE_SSH_KEY")
fi
if [[ -n "$REMOTE_SSH_OPTS" ]]; then
    # shellcheck disable=SC2206
    EXTRA_SSH_ARGS=($REMOTE_SSH_OPTS)
    SSH_ARGS+=("${EXTRA_SSH_ARGS[@]}")
    SCP_ARGS+=("${EXTRA_SSH_ARGS[@]}")
fi

echo "[remote-teebox-smoke] building teeBoxZip"
"$REPO_ROOT/gradlew" --no-daemon teeBoxZip >/dev/null

echo "[remote-teebox-smoke] uploading $DIST_ZIP to $REMOTE_HOST:$REMOTE_DIR"
ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "rm -rf '$REMOTE_DIR' && mkdir -p '$REMOTE_DIR'"
scp "${SCP_ARGS[@]}" "$DIST_ZIP" "$REMOTE_HOST:$REMOTE_DIR/propertee-teebox-dist.zip" >/dev/null

set +e
ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "
    set -e
    cd '$REMOTE_DIR'
    unzip -oq propertee-teebox-dist.zip -d teebox
    cd teebox
    chmod +x ./bin/run-teebox.sh
    mkdir -p smoke-scripts smoke-data
    cat > smoke-scripts/smoke.pt <<'EOF'
PRINT(\"teebox smoke ok\")
EOF
    cat > conf/teebox.properties <<'EOF'
propertee.teebox.bind=127.0.0.1
propertee.teebox.port=$TEEBOX_PORT
propertee.teebox.scriptsRoot=$REMOTE_DIR/teebox/smoke-scripts
propertee.teebox.dataDir=$REMOTE_DIR/teebox/smoke-data
EOF
    ./bin/run-teebox.sh > teebox.log 2>&1 &
    server_pid=\$!
    printf '%s\n' \"\$server_pid\" > teebox.pid

    ready=0
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        if curl -fsS 'http://127.0.0.1:$TEEBOX_PORT/admin' >/dev/null 2>&1; then
            ready=1
            break
        fi
        sleep 1
    done

    if [[ \"\$ready\" != \"1\" ]]; then
        echo 'TeeBox did not become ready' >&2
        cat teebox.log >&2 || true
        kill \"\$server_pid\" >/dev/null 2>&1 || true
        wait \"\$server_pid\" >/dev/null 2>&1 || true
        exit 1
    fi

    curl -fsS 'http://127.0.0.1:$TEEBOX_PORT/admin' >/dev/null
    kill \"\$server_pid\"
    wait \"\$server_pid\" || true
" 2>&1 | tee "$LOG_FILE"
REMOTE_STATUS=${PIPESTATUS[0]}
set -e

ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "test -f '$REMOTE_DIR/teebox/teebox.log' && cat '$REMOTE_DIR/teebox/teebox.log'" > "$ARTIFACT_DIR/remote-teebox.log" 2>/dev/null || true

if [[ "${KEEP_REMOTE:-0}" != "1" ]]; then
    ssh "${SSH_ARGS[@]}" "$REMOTE_HOST" "rm -rf '$REMOTE_DIR'" >/dev/null 2>&1 || true
fi

echo "[remote-teebox-smoke] remote exit code: $REMOTE_STATUS"
echo "[remote-teebox-smoke] log file: $LOG_FILE"
exit "$REMOTE_STATUS"
