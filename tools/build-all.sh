#!/bin/sh
# Build and game-test BeezzCraft for every supported Minecraft version; jars land in dist/.
#   JAVA_HOME=<JDK 25> tools/build-all.sh [--no-test]
set -e
cd "$(dirname "$0")/.."
mkdir -p dist
TASKS="build runClientGameTest"
[ "$1" = "--no-test" ] && TASKS="build"
while read -r mc fabric; do
  [ -z "$mc" ] && continue
  echo "== Minecraft $mc (Fabric API $fabric)"
  ./gradlew $TASKS -Pminecraft_version="$mc" -Pfabric_version="$fabric" --console=plain -q </dev/null
  cp build/libs/beezzcraft-"$(grep ^mod_version= gradle.properties | cut -d= -f2)"+"$(echo "$mc" | cut -d. -f1-2)".jar dist/
done <<VERSIONS
26.3 0.164.0+26.3
26.2 0.161.0+26.2
26.1 0.155.3+26.1.2
VERSIONS
ls -l dist/*.jar
