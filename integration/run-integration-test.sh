#!/bin/sh
# Boots a Paper server in Docker with the built ArtMap jar + ProtocolLib and checks it enables, then runs
# the paint bot and the regression suite (either can be switched off). POSIX sh: the CI image has no bash.

# -e/-u for the setup steps; the polling loop disables -e on purpose so the log is always captured.
set -eu

# --- Configuration (all overridable from the environment) ---
MC_VERSION="${MC_VERSION:-26.1.2}"            # Minecraft / Paper version (must be one the bots can speak)
# java25 tag: AnvilGUI's 1.26 wrapper and the ProtocolLib dev build ship Java 25 bytecode for Paper's remapper.
SERVER_IMAGE="${SERVER_IMAGE:-itzg/minecraft-server:java25}"
PROTOCOLLIB_VERSION="${PROTOCOLLIB_VERSION:-dev-build}"  # ProtocolLib GitHub release tag
TIMEOUT="${TIMEOUT:-600}"                     # seconds to wait for the server to start
RUN_PAINT_TEST="${RUN_PAINT_TEST:-true}"      # run bot/paint-bot.js and verify Art.db (false to skip)
RUN_REGRESSION_TEST="${RUN_REGRESSION_TEST:-true}"   # run bot/regression.js, #376 and #377 (false to skip)
RUN_FEATURE_TEST="${RUN_FEATURE_TEST:-true}"         # run bot/features.js: menus, save dialog, commands, reload (false to skip)
RCON_PASSWORD="${RCON_PASSWORD:-artmap-e2e}"  # used by the bots

# CI_JOB_ID keeps container/volume names unique so parallel jobs don't collide.
JOB_SUFFIX="${CI_JOB_ID:-local-$$}"

CONTAINER_NAME="artmap-server-${JOB_SUFFIX}"
COPY_HELPER="artmap-copy-${JOB_SUFFIX}"
VOLUME_NAME="artmap-plugins-${JOB_SUFFIX}"
BOT_VOLUME_NAME="artmap-bot-${JOB_SUFFIX}"
BOT_HELPER="artmap-bot-copy-${JOB_SUFFIX}"

LOG_FILE="integration/server.log"

PROTOCOLLIB_URL="https://github.com/dmulloy2/ProtocolLib/releases/download/${PROTOCOLLIB_VERSION}/ProtocolLib.jar"

# --- Cleanup: always runs on exit ---
cleanup() {
    echo ""
    echo "--- Cleanup ---"
    docker stop  "$CONTAINER_NAME" 2>/dev/null || true
    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
    docker rm -f "$COPY_HELPER"    2>/dev/null || true
    docker rm -f "$BOT_HELPER"     2>/dev/null || true
    docker volume rm "$VOLUME_NAME" 2>/dev/null || true
    docker volume rm "$BOT_VOLUME_NAME" 2>/dev/null || true
}
trap cleanup EXIT

# --- Locate the built ArtMap jar. An instrumented *-it.jar (mvn -P it-coverage) wins and switches on
# coverage collection; otherwise the newest plain jar, so a stale one left in target/ is never tested ---
JAR=$(ls -t plugin/target/ArtMap-*-it.jar 2>/dev/null | grep -v 'original-' | head -1 || true)
COVERAGE=false
if [ -n "$JAR" ]; then
    COVERAGE=true
else
    JAR=$(ls -t plugin/target/ArtMap-*.jar 2>/dev/null \
            | grep -v '\-sources' \
            | grep -v '\-javadoc' \
            | grep -v 'original-' \
            | head -1 || true)
fi

if [ -z "$JAR" ]; then
    echo "ERROR: No ArtMap JAR found in plugin/target/. Was the build stage run?"
    exit 1
fi
echo "Using JAR: $JAR (coverage: ${COVERAGE})"
EXEC_FILE="integration/jacoco-it.exec"
JACOCO_VERSION="${JACOCO_VERSION:-0.8.15}"     # must match the jacoco-maven-plugin that instrumented the jar
JACOCO_AGENT_URL="https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar"
# The agent is the runtime the offline-instrumented classes call into. It cannot live inside the plugin (Paper
# closes plugin classloaders before exit, so the dump-on-exit hook would fail) and the javaagent's own place, the
# system class path, is not visible to Paper's plugin loaders either; the bootstrap class path is visible to all.
# With includes matching nothing the agent instruments no classes itself and only collects and writes the data.
JACOCO_AGENT_JAR="/plugins/.jacoco/jacocoagent.jar"
JACOCO_JVM_OPTS="-Xbootclasspath/a:${JACOCO_AGENT_JAR} -javaagent:${JACOCO_AGENT_JAR}=destfile=/data/jacoco-it.exec,output=file,dumponexit=true,includes=none.*"

# --- Plugin volume --- dind cannot bind-mount the job workspace, so stage the jars in a named volume:
# wget ProtocolLib straight into it, then docker cp the ArtMap jar in through a helper container.
echo ""
echo "--- Docker info ---"
docker info

echo ""
echo "--- Setting up plugin volume ---"
docker volume create "$VOLUME_NAME"

# -S shows the redirect chain (GitHub 302 -> S3); --no-check-certificate covers a thin runner CA bundle.
echo "Downloading ProtocolLib ${PROTOCOLLIB_VERSION}..."
docker run --rm \
    -v "${VOLUME_NAME}:/plugins" \
    alpine \
    sh -c "wget -S --no-check-certificate -O /plugins/ProtocolLib.jar '${PROTOCOLLIB_URL}' 2>&1 \
           && echo 'ProtocolLib downloaded successfully.' \
           || { echo 'ERROR: ProtocolLib download failed'; exit 1; }"

echo "Copying ArtMap JAR into volume..."
docker create --name "$COPY_HELPER" -v "${VOLUME_NAME}:/plugins" alpine echo
docker cp "$JAR" "${COPY_HELPER}:/plugins/ArtMap.jar"
docker rm "$COPY_HELPER"
echo "ArtMap JAR copied."

if [ "$COVERAGE" = "true" ]; then
    # A sub-directory of /plugins: Paper only scans top-level jars, so the agent is not mistaken for a plugin.
    echo "Downloading JaCoCo agent ${JACOCO_VERSION}..."
    docker run --rm \
        -v "${VOLUME_NAME}:/plugins" \
        alpine \
        sh -c "mkdir -p /plugins/.jacoco && wget -q --no-check-certificate -O /plugins/.jacoco/jacocoagent.jar '${JACOCO_AGENT_URL}' \
               && echo 'JaCoCo agent downloaded successfully.' \
               || { echo 'ERROR: JaCoCo agent download failed'; exit 1; }"
fi

# --- Start the Paper server ---
echo ""
echo "--- Starting Paper ${MC_VERSION} server ---"
docker run -d \
    --name "$CONTAINER_NAME" \
    -e EULA=TRUE \
    -e TYPE=PAPER \
    -e VERSION="$MC_VERSION" \
    -e MEMORY=1G \
    -e ONLINE_MODE=FALSE \
    -e ENABLE_RCON="$([ "$RUN_PAINT_TEST" = "true" ] || [ "$RUN_REGRESSION_TEST" = "true" ] || [ "$RUN_FEATURE_TEST" = "true" ] && echo TRUE || echo FALSE)" \
    -e RCON_PASSWORD="$RCON_PASSWORD" \
    -e JVM_OPTS="$([ "$COVERAGE" = "true" ] && echo "$JACOCO_JVM_OPTS")" \
    -v "${VOLUME_NAME}:/plugins" \
    "$SERVER_IMAGE"

# --- Wait for the server --- poll the logs for "Done (" or the timeout. -e is off so a transient docker
# failure cannot abort the loop before the log is saved.
echo ""
echo "--- Waiting for server to start (timeout: ${TIMEOUT}s) ---"
START_TIME=$(date +%s)
RESULT="timeout"
mkdir -p integration

set +e
while true; do
    ELAPSED=$(( $(date +%s) - START_TIME ))
    if [ "$ELAPSED" -gt "$TIMEOUT" ]; then
        RESULT="timeout"
        break
    fi

    # Bail out early if the container died unexpectedly
    RUNNING=$(docker inspect "$CONTAINER_NAME" --format '{{.State.Running}}' 2>/dev/null || echo "false")
    if [ "$RUNNING" != "true" ]; then
        RESULT="container_stopped"
        break
    fi

    LOGS=$(docker logs "$CONTAINER_NAME" 2>&1)

    # Plugin JAR rejected at load time
    if echo "$LOGS" | grep -q "Could not load 'plugins/ArtMap.jar'"; then
        RESULT="plugin_load_error"
        break
    fi

    # Server finished starting – now check plugin outcome
    if echo "$LOGS" | grep -q "Done ("; then
        if echo "$LOGS" | grep -qi "Enabling ArtMap"; then
            if echo "$LOGS" | grep -qi "Disabling ArtMap"; then
                RESULT="plugin_disabled"
            else
                RESULT="success"
            fi
        else
            RESULT="plugin_not_loaded"
        fi
        break
    fi

    sleep 10
done
set -e

# --- Paint test --- run the mineflayer bot from a node container on the server's bridge network (sources
# docker-cp'd into a volume, same dind constraint as the jar), then verify strokes landed in Art.db.
if [ "$RUN_PAINT_TEST" = "true" ] && [ "$RESULT" = "success" ]; then
    echo ""
    echo "--- Paint test: running mineflayer bot ---"
    set +e

    SERVER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_NAME")
    echo "Server IP: ${SERVER_IP}"

    docker volume create "$BOT_VOLUME_NAME"
    docker create --name "$BOT_HELPER" -v "${BOT_VOLUME_NAME}:/bot" alpine echo
    docker cp integration/bot/. "${BOT_HELPER}:/bot/"
    docker rm "$BOT_HELPER"

    docker run --rm \
        -v "${BOT_VOLUME_NAME}:/bot" \
        -w /bot \
        -e MC_HOST="$SERVER_IP" \
        -e MC_VERSION="$MC_VERSION" \
        -e RCON_PASSWORD="$RCON_PASSWORD" \
        node:20-alpine \
        sh -c "npm install --no-audit --no-fund --loglevel=error && node paint-bot.js"
    BOT_EXIT=$?

    if [ "$BOT_EXIT" -ne 0 ]; then
        RESULT="paint_bot_failed"
    else
        echo ""
        echo "--- Paint test: verifying Art.db ---"
        docker cp "${CONTAINER_NAME}:/data/plugins/ArtMap/Art.db" integration/Art.db
        if [ ! -f integration/Art.db ]; then
            RESULT="paint_verify_failed"
        else
            # Painted (or in-progress) maps land in the maps table as blobs.
            MAPS=$(docker run --rm -i alpine \
                sh -c 'apk add -q sqlite && cat > /tmp/Art.db && sqlite3 /tmp/Art.db "SELECT count(*) FROM maps WHERE length(map) > 0;"' \
                < integration/Art.db)
            echo "Maps with painted data: ${MAPS:-0}"
            if [ "${MAPS:-0}" -gt 0 ] 2>/dev/null; then
                RESULT="success"
            else
                RESULT="paint_verify_failed"
            fi
        fi
    fi
    set -e
fi

# --- Regression suite --- #376 (a destroyed easel must not strand a session; item frames keep working) and
# #377 (a regular map laminates in a cartography table with no ArtMap error). See integration/README.md.
if [ "$RUN_REGRESSION_TEST" = "true" ] && [ "$RESULT" = "success" ]; then
    echo ""
    echo "--- Regression: #376 stranded art session, #377 cartography table ---"
    set +e

    SERVER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_NAME")

    docker volume create "$BOT_VOLUME_NAME" >/dev/null 2>&1
    docker create --name "$BOT_HELPER" -v "${BOT_VOLUME_NAME}:/bot" alpine echo >/dev/null
    docker cp integration/bot/. "${BOT_HELPER}:/bot/"
    docker rm "$BOT_HELPER" >/dev/null

    docker run --rm \
        -v "${BOT_VOLUME_NAME}:/bot" \
        -w /bot \
        -e MC_HOST="$SERVER_IP" \
        -e MC_VERSION="$MC_VERSION" \
        -e RCON_PASSWORD="$RCON_PASSWORD" \
        node:20-alpine \
        sh -c "npm install --no-audit --no-fund --loglevel=error && node regression.js"
    REG_EXIT=$?

    [ "$REG_EXIT" -ne 0 ] && RESULT="regression_failed"
    set -e
fi

# --- Feature suite --- the server-only surface: inventory menus and their close paths, the paintbrush save
# dialog, editing/copying saved artworks, the admin commands, a plain player, session ends by death/kick/chunk
# unload, and a plugin reload with live sessions. See integration/README.md.
if [ "$RUN_FEATURE_TEST" = "true" ] && [ "$RESULT" = "success" ]; then
    echo ""
    echo "--- Feature suite: menus, save dialog, commands, sessions, reload ---"
    set +e

    SERVER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_NAME")

    docker volume create "$BOT_VOLUME_NAME" >/dev/null 2>&1
    docker create --name "$BOT_HELPER" -v "${BOT_VOLUME_NAME}:/bot" alpine echo >/dev/null
    docker cp integration/bot/. "${BOT_HELPER}:/bot/"
    docker rm "$BOT_HELPER" >/dev/null

    docker run --rm \
        -v "${BOT_VOLUME_NAME}:/bot" \
        -w /bot \
        -e MC_HOST="$SERVER_IP" \
        -e MC_VERSION="$MC_VERSION" \
        -e RCON_PASSWORD="$RCON_PASSWORD" \
        node:20-alpine \
        sh -c "npm install --no-audit --no-fund --loglevel=error && node features.js"
    FEAT_EXIT=$?

    [ "$FEAT_EXIT" -ne 0 ] && RESULT="feature_failed"
    set -e
fi

# --- Coverage --- the exec file is written by a JVM shutdown hook, so the server has to stop cleanly
# (docker stop hands Paper a "stop"; give it time to save the world) before the file is copied out.
if [ "$COVERAGE" = "true" ]; then
    echo ""
    echo "--- Coverage: stopping the server and collecting ${EXEC_FILE} ---"
    set +e
    docker stop -t 120 "$CONTAINER_NAME" >/dev/null 2>&1
    rm -f "$EXEC_FILE"
    docker cp "${CONTAINER_NAME}:/data/jacoco-it.exec" "$EXEC_FILE" 2>/dev/null
    if [ -s "$EXEC_FILE" ]; then
        echo "Collected $(wc -c < "$EXEC_FILE") bytes of execution data."
    else
        echo "WARNING: no execution data was written (did the server stop cleanly?)."
        [ "$RESULT" = "success" ] && RESULT="coverage_missing"
    fi
    set -e
fi

# --- Save the full server log (CI artifact) ---
echo ""
echo "--- Saving server log to ${LOG_FILE} ---"
docker logs "$CONTAINER_NAME" > "$LOG_FILE" 2>&1 || true

# --- Report ---
echo ""
echo "=============================="
echo " Integration Test: ${RESULT}"
echo "=============================="
echo ""

case "$RESULT" in
    success)
        echo "PASS: ArtMap loaded and enabled successfully on Paper ${MC_VERSION}."
        if [ "$RUN_PAINT_TEST" = "true" ]; then
            echo "PASS: paint bot strokes were persisted to Art.db."
        fi
        if [ "$RUN_REGRESSION_TEST" = "true" ]; then
            echo "PASS: #376 - a destroyed easel did not strand the art session."
            echo "PASS: #377 - regular maps laminate like vanilla with no ArtMap error."
        fi
        if [ "$RUN_FEATURE_TEST" = "true" ]; then
            echo "PASS: feature suite - menus, save dialog, commands, sessions and reload behave as documented."
        fi
        if [ "$COVERAGE" = "true" ]; then
            echo "PASS: coverage data collected in ${EXEC_FILE}."
        fi
        exit 0
        ;;
    coverage_missing)
        echo "FAIL: every phase passed but the server wrote no coverage data."
        echo "      The offline JaCoCo runtime dumps on JVM exit; check ${LOG_FILE} for the shutdown."
        exit 1
        ;;
    feature_failed)
        echo "FAIL: feature suite - see the failure list in the bot output above."
        echo "      Each entry names the check and the assertion that failed; the checks are"
        echo "      described in integration/README.md. Server side: ${LOG_FILE}."
        exit 1
        ;;
    regression_failed)
        echo "FAIL: regression suite - see the failure list in the bot output above."
        echo "      #376: a destroyed easel stranded the player in ArtMap's artists"
        echo "            map, so item frames silently stopped working for them."
        echo "      #377: a regular map produced an ArtMap error in the cartography"
        echo "            table, or the lamination did not complete."
        echo "      Server side: ${LOG_FILE}."
        exit 1
        ;;
    paint_bot_failed)
        echo "FAIL: the paint bot exited with an error (see output above)."
        echo "      Check ${LOG_FILE} for the server side."
        exit 1
        ;;
    paint_verify_failed)
        echo "FAIL: the paint bot ran but no painted map data was found in Art.db."
        echo "      Check ${LOG_FILE} for the server side."
        exit 1
        ;;
    plugin_disabled)
        echo "FAIL: ArtMap was disabled by the server after loading."
        echo "      Check ${LOG_FILE} for the cause."
        cat "$LOG_FILE"
        exit 1
        ;;
    plugin_not_loaded)
        echo "FAIL: Server started but ArtMap did not appear in the plugin list."
        echo "      Check ${LOG_FILE} for the cause."
        cat "$LOG_FILE"
        exit 1
        ;;
    plugin_load_error)
        echo "FAIL: ArtMap JAR could not be loaded by the server."
        echo "      Check ${LOG_FILE} for the cause."
        cat "$LOG_FILE"
        exit 1
        ;;
    container_stopped)
        echo "FAIL: Minecraft server container stopped unexpectedly."
        echo "      Check ${LOG_FILE} for the cause."
        cat "$LOG_FILE"
        exit 1
        ;;
    timeout)
        echo "FAIL: Server did not finish starting within ${TIMEOUT} seconds."
        echo "      Check ${LOG_FILE} for the cause."
        cat "$LOG_FILE"
        exit 1
        ;;
    *)
        echo "FAIL: Unknown result '${RESULT}'."
        cat "$LOG_FILE"
        exit 1
        ;;
esac
