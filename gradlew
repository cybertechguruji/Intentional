#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1
if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi
if ! command -v "$JAVA_EXE" >/dev/null 2>&1 && [ ! -x "$JAVA_EXE" ]; then
  echo "Java was not found. Open this project in Android Studio or install a Java 17 runtime." >&2
  exit 1
fi
exec "$JAVA_EXE" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
