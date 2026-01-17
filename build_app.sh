#!/bin/bash

# Build script for Contrib Codex app
# Creates .app for Mac and .exe for Windows (when run on respective platforms)

echo "Building Contrib Codex..."

# 1. Clean and Package with dependencies
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Maven build failed!"
    exit 1
fi

# 2. Identify the JAR and libs
APP_VERSION="1.0-SNAPSHOT"
MAIN_JAR="target/contrib_metric-${APP_VERSION}.jar"
LIBS_DIR="target/libs"
MAIN_CLASS="dev.grahamhill.Main"

# 3. Use jpackage to create the installer
# Note: jpackage requires JDK 14+

PLATFORM="unknown"
case "$(uname -s)" in
    Linux*)     PLATFORM=linux;;
    Darwin*)    PLATFORM=macos;;
    CYGWIN*)    PLATFORM=windows;;
    MINGW*)     PLATFORM=windows;;
    *)          PLATFORM="unknown"
esac

echo "Detected platform: ${PLATFORM}"

if [ "${PLATFORM}" == "macos" ]; then
    echo "Creating macOS .app..."
    jpackage \
      --type app-image \
      --input target \
      --dest dist \
      --name "ContribCodex" \
      --main-jar "contrib_metric-${APP_VERSION}.jar" \
      --main-class ${MAIN_CLASS} \
      --module-path "${LIBS_DIR}" \
      --add-modules javafx.controls,javafx.fxml,javafx.swing \
      --vendor "Graham Hill" \
      --description "Contrib Codex Git Analytics" \
      --app-version "1.0.0" \
      --mac-package-name "ContribCodex"
    
    # Also create a DMG if needed: --type dmg
    
elif [ "${PLATFORM}" == "windows" ]; then
    echo "Creating Windows .exe..."
    jpackage \
      --type exe \
      --input target \
      --dest dist \
      --name "ContribCodex" \
      --main-jar "contrib_metric-${APP_VERSION}.jar" \
      --main-class ${MAIN_CLASS} \
      --module-path "${LIBS_DIR}" \
      --add-modules javafx.controls,javafx.fxml,javafx.swing \
      --vendor "Graham Hill" \
      --description "Contrib Codex Git Analytics" \
      --app-version "1.0.0" \
      --win-dir-chooser \
      --win-shortcut \
      --win-menu
else
    echo "Platform ${PLATFORM} not supported for native packaging by this script."
fi

if [ $? -eq 0 ]; then
    echo "Build successful! Check the 'dist' directory."
else
    echo "jpackage failed!"
    exit 1
fi
