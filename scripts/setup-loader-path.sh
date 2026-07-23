#!/usr/bin/env bash
# ============================================================================
# setup-loader-path.sh
#
# One-time setup for Nexus 3.94: creates loader.properties in the Nexus data
# directory so Spring Boot's PropertiesLauncher adds the deploy/ directory to
# the application classpath.
#
# Without this file Nexus 3.94 ignores everything in deploy/ — the fat-JAR
# launcher has no other mechanism to load external JARs.
#
# Usage (Docker):
#   CONTAINER=nexus-dev ./scripts/setup-loader-path.sh
#
# Usage (local install):
#   NEXUS_DATA=/opt/sonatype/sonatype-work/nexus3 ./scripts/setup-loader-path.sh
# ============================================================================

set -euo pipefail

CONTAINER="${CONTAINER:-nexus-dev}"
NEXUS_DATA="${NEXUS_DATA:-}"

# ─── Docker path ─────────────────────────────────────────────────────────────
if [ -z "$NEXUS_DATA" ] && command -v docker &>/dev/null; then
  if docker ps --format "{{.Names}}" | grep -q "^${CONTAINER}$" 2>/dev/null; then
    echo "Using Docker container: $CONTAINER"

    # The nexus startup script puts sonatype-work/nexus3/etc on the classpath.
    # PropertiesLauncher reads loader.properties from any classpath entry.
    LOADER_PROPS_PATH="/opt/sonatype/nexus/../sonatype-work/nexus3/etc/loader.properties"

    docker exec "$CONTAINER" sh -c "
      cat > $LOADER_PROPS_PATH <<'EOF'
# Added by nexus-developer-portal setup-loader-path.sh
# Makes PropertiesLauncher add deploy/ JARs to the Spring Boot application classpath.
loader.path=/opt/sonatype/nexus/deploy
EOF
      echo 'Created: $LOADER_PROPS_PATH'
      cat $LOADER_PROPS_PATH
    "

    echo ""
    echo "Restarting Nexus to pick up loader.properties..."
    docker restart "$CONTAINER"
    echo ""
    echo "Waiting for Nexus to start (~60 s)..."
    sleep 30
    for i in $(seq 1 12); do
      if curl -sf http://localhost:8081/service/rest/v1/status >/dev/null 2>&1; then
        echo "Nexus is up."
        break
      fi
      echo "  ... still waiting ($((i * 5)) s)"
      sleep 5
    done
    echo ""
    echo "loader.properties is in place. Now deploy your plugin JAR:"
    echo "  docker cp target/nexus-developer-portal-1.0.0-SNAPSHOT.jar $CONTAINER:/opt/sonatype/nexus/deploy/"
    echo "  docker restart $CONTAINER"
    exit 0
  fi
fi

# ─── Local install path ──────────────────────────────────────────────────────
if [ -z "$NEXUS_DATA" ]; then
  echo "ERROR: Could not find Docker container '$CONTAINER' and NEXUS_DATA is not set."
  echo ""
  echo "Set NEXUS_DATA to your Nexus data directory and re-run:"
  echo "  NEXUS_DATA=/opt/sonatype/sonatype-work/nexus3 ./scripts/setup-loader-path.sh"
  exit 1
fi

ETC_DIR="$NEXUS_DATA/etc"
mkdir -p "$ETC_DIR"

cat > "$ETC_DIR/loader.properties" <<EOF
# Added by nexus-developer-portal setup-loader-path.sh
loader.path=/opt/sonatype/nexus/deploy
EOF

echo "Created: $ETC_DIR/loader.properties"
echo ""
echo "Restart Nexus and then deploy the plugin JAR:"
echo "  cp target/nexus-developer-portal-1.0.0-SNAPSHOT.jar \$NEXUS_HOME/deploy/"
