#!/usr/bin/env bash
# ============================================================================
# install-nexus-deps.sh
#
# Extracts Nexus REST plugin-development JARs from the Nexus 3.94 fat JAR
# and installs them into your local Maven repository.
#
# Run once per machine / per Nexus version upgrade:
#
#   NEXUS_JAR=/opt/sonatype/nexus/bin/sonatype-nexus-repository-3.94.0-12.jar \
#     ./scripts/install-nexus-deps.sh
#
# The script reads NEXUS_VERSION from pom.xml automatically if xmllint is
# available; otherwise falls back to the NEXUS_VERSION environment variable.
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POM="$SCRIPT_DIR/../pom.xml"

# ─── Resolve version ────────────────────────────────────────────────────────
if command -v xmllint &>/dev/null && [ -f "$POM" ]; then
  NEXUS_VERSION=$(xmllint --xpath \
    "string(//*[local-name()='nexus.version'])" "$POM" 2>/dev/null || true)
fi
NEXUS_VERSION="${NEXUS_VERSION:-3.94.0-12}"

# ─── Locate the fat JAR ─────────────────────────────────────────────────────
# Default: try to find it via a running Docker container
if [ -z "${NEXUS_JAR:-}" ]; then
  if command -v docker &>/dev/null; then
    CONTAINER=$(docker ps --filter "ancestor=sonatype/nexus3" --format "{{.Names}}" 2>/dev/null | head -1 || true)
    if [ -n "$CONTAINER" ]; then
      echo "Auto-detected Nexus container: $CONTAINER"
      NEXUS_JAR_REMOTE="/opt/sonatype/nexus/bin/sonatype-nexus-repository-${NEXUS_VERSION}.jar"
      TMP_JAR="/tmp/sonatype-nexus-repository-${NEXUS_VERSION}.jar"
      echo "Copying fat JAR from container (this may take ~30 s)..."
      docker cp "$CONTAINER:$NEXUS_JAR_REMOTE" "$TMP_JAR"
      NEXUS_JAR="$TMP_JAR"
    fi
  fi
fi

if [ -z "${NEXUS_JAR:-}" ] || [ ! -f "$NEXUS_JAR" ]; then
  echo ""
  echo "ERROR: Cannot locate the Nexus fat JAR."
  echo "       Set NEXUS_JAR to the path of sonatype-nexus-repository-${NEXUS_VERSION}.jar"
  echo ""
  echo "  Option A — via running Docker container:"
  echo "    docker cp nexus-dev:/opt/sonatype/nexus/bin/sonatype-nexus-repository-${NEXUS_VERSION}.jar /tmp/"
  echo "    NEXUS_JAR=/tmp/sonatype-nexus-repository-${NEXUS_VERSION}.jar ./scripts/install-nexus-deps.sh"
  echo ""
  echo "  Option B — local installation:"
  echo "    NEXUS_JAR=/opt/sonatype/nexus/bin/sonatype-nexus-repository-${NEXUS_VERSION}.jar ./scripts/install-nexus-deps.sh"
  exit 1
fi

echo "================================================================"
echo "  Nexus Developer Portal — dependency installer"
echo "  NEXUS_JAR     : $NEXUS_JAR"
echo "  NEXUS_VERSION : $NEXUS_VERSION"
echo "================================================================"

if ! command -v mvn &>/dev/null; then
  echo "ERROR: mvn not found. Install Maven 3.8+."
  exit 1
fi

# ─── Helper: extract one JAR from the fat JAR, then install it ──────────────
install_from_fat_jar() {
  local artifact_id="$1"
  local group_id="${2:-org.sonatype.nexus}"
  local inner_path="BOOT-INF/lib/${artifact_id}-${NEXUS_VERSION}.jar"
  local tmp_jar="/tmp/${artifact_id}-${NEXUS_VERSION}.jar"

  echo ""
  echo "  Extracting: $inner_path"
  if ! unzip -p "$NEXUS_JAR" "$inner_path" > "$tmp_jar" 2>/dev/null || [ ! -s "$tmp_jar" ]; then
    echo "  SKIP (not found inside fat JAR): $inner_path"
    rm -f "$tmp_jar"
    return 0
  fi

  echo "  Installing: ${group_id}:${artifact_id}:${NEXUS_VERSION}"
  mvn install:install-file \
    -q \
    -Dfile="$tmp_jar" \
    -DgroupId="$group_id" \
    -DartifactId="$artifact_id" \
    -Dversion="$NEXUS_VERSION" \
    -Dpackaging=jar \
    -DgeneratePom=true

  rm -f "$tmp_jar"
}

# ─── Install the Nexus APIs our plugin compiles against ─────────────────────
install_from_fat_jar "nexus-core"
# In 3.94 RepositoryManager moved from nexus-repository to nexus-repository-config
install_from_fat_jar "nexus-repository-config"
install_from_fat_jar "nexus-rest"
install_from_fat_jar "nexus-security"
install_from_fat_jar "nexus-common"
# RepositoryManager's interface hierarchy references these at compile time
install_from_fat_jar "nexus-datastore-api"
install_from_fat_jar "nexus-datastore"
# Datastore Fluent content API (ContentFacet, FluentComponents) for package providers
install_from_fat_jar "nexus-repository-content"
# Content/Payload types returned by FluentAsset.download()
install_from_fat_jar "nexus-repository-view"
# RepositoryPermissionChecker — filters repos to those the caller may read
install_from_fat_jar "nexus-repository-services"

echo ""
echo "================================================================"
echo "  Done. You can now build with (Java 21+ required):"
echo "    JAVA_HOME=/path/to/java21 mvn clean package"
echo "================================================================"
echo ""
