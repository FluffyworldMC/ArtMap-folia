#!/usr/bin/env bash
# Boot the itzg server once at image-build time (Paper downloads and patches the Mojang jar, generates the world),
# stop it cleanly, and commit /data into the image. Run from the Dockerfile's RUN step.
set -eu

# itzg's entrypoint runs as root and drops to uid 1000 ("server") via gosu; the
# baked /data must end up owned by that uid so the runtime server can read it.
START_TIMEOUT="${BAKE_TIMEOUT:-300}" # seconds to wait for "Done (" at build time

echo "[bake] starting server to pre-download + patch (timeout ${START_TIMEOUT}s)..."

# Launch the normal entrypoint in the background and capture its log.
/start > /tmp/bake.log 2>&1 &
START_PID=$!

deadline=$(( $(date +%s) + START_TIMEOUT ))
ready=""
while [ -z "$ready" ]; do
    if ! kill -0 "$START_PID" 2>/dev/null; then
        echo "[bake] ERROR: server process exited before becoming ready"
        cat /tmp/bake.log
        exit 1
    fi
    if grep -q 'Done (' /tmp/bake.log 2>/dev/null; then
        ready=1
        break
    fi
    if [ "$(date +%s)" -gt "$deadline" ]; then
        echo "[bake] ERROR: server did not finish starting within ${START_TIMEOUT}s"
        cat /tmp/bake.log
        exit 1
    fi
    sleep 3
done

echo "[bake] server ready; stopping it cleanly..."
# Ask the server to stop via RCON-less console: send SIGTERM to the runner, which
# itzg traps and forwards as a graceful 'stop' to the JVM.
kill -TERM "$START_PID" 2>/dev/null || true

# Wait for the JVM to flush and exit so the world/region files are consistent.
stop_deadline=$(( $(date +%s) + 60 ))
while kill -0 "$START_PID" 2>/dev/null; do
    if [ "$(date +%s)" -gt "$stop_deadline" ]; then
        echo "[bake] WARN: server did not stop within 60s; killing"
        kill -KILL "$START_PID" 2>/dev/null || true
        break
    fi
    sleep 2
done

# Confirm the patched Mojang jar landed so we don't ship a half-baked image.
if ! ls /data/*.jar >/dev/null 2>&1; then
    echo "[bake] ERROR: no server jar found in /data after bake"
    ls -la /data || true
    exit 1
fi

# /data is a VOLUME, so build-time writes are discarded; copy to a plain path that survives, and
# seed-and-start.sh restores it at boot.
echo "[bake] copying /data -> /baked-data so it persists into the image..."
rm -rf /baked-data
cp -a /data /baked-data
# The patched Mojang jar lives under versions/; verify it baked.
if ! find /baked-data/versions -type f -name '*.jar' | grep -q .; then
    echo "[bake] ERROR: no patched server jar under /baked-data/versions"
    find /baked-data -maxdepth 2 -type d || true
    exit 1
fi

echo "[bake] done. /baked-data contents:"
ls -la /baked-data
