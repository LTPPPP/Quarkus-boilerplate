#!/bin/sh
# Maven Wrapper script
# Adapted from https://github.com/apache/maven-wrapper

set -euf

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd -- "$(dirname -- "$0")" && pwd -P)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -r "$MAVEN_WRAPPER_PROPERTIES" ]; then
  MAVEN_WRAPPER_URL=$(grep 'wrapperUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2- | tr -d '[:space:]')
  DISTRIBUTION_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2- | tr -d '[:space:]')
fi

DISTRIBUTION_URL="${DISTRIBUTION_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip}"
MAVEN_HOME="${MAVEN_HOME:-$MAVEN_PROJECTBASEDIR/.mvn/wrapper/dists}"
MAVEN_DIR="$MAVEN_HOME/apache-maven-3.9.9"

if [ ! -d "$MAVEN_DIR" ]; then
  echo "Downloading Maven from $DISTRIBUTION_URL ..."
  mkdir -p "$MAVEN_HOME"
  TMPFILE=$(mktemp)
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "$TMPFILE" "$DISTRIBUTION_URL"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$TMPFILE" "$DISTRIBUTION_URL"
  else
    echo "Error: Neither curl nor wget found. Cannot download Maven." >&2
    exit 1
  fi
  unzip -q -o "$TMPFILE" -d "$MAVEN_HOME"
  rm -f "$TMPFILE"
  chmod +x "$MAVEN_DIR/bin/mvn"
  echo "Maven downloaded successfully."
fi

exec "$MAVEN_DIR/bin/mvn" "$@"
