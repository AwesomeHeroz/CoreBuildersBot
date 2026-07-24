#!/usr/bin/env sh

# Minimal Gradle Wrapper launcher. Version/distribution settings live only in
# gradle/wrapper/gradle-wrapper.properties.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1 && [ ! -x "$JAVA_CMD" ]; then
  echo "ERROR: Java was not found. Set JAVA_HOME or add java to PATH." >&2
  exit 1
fi

exec "$JAVA_CMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
  -Dorg.gradle.appname=gradlew \
  -classpath "$WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
