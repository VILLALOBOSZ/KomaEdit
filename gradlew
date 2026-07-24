#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: Java was not found. Set JAVA_HOME or add java to PATH." >&2
    exit 1
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname=gradlew \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
