#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
APP_HOME=`pwd -P`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_HOME=/usr/lib/jvm/java-17-openjdk
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
