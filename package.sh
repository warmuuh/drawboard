#!/bin/bash
set -e

# Activate the correct Java version
source ~/.sdkman/bin/sdkman-init.sh
sdk env

echo "Building application..."
mvn clean package -DskipTests

echo "Creating DMG installer with jpackage..."
jpackage \
  --type dmg \
  --name Drawboard \
  --app-version 1.0 \
  --vendor "Drawboard" \
  --description "A notebook application with free-form canvas pages" \
  --dest target/dist \
  --input target \
  --main-jar drawboard-0.1.0-SNAPSHOT.jar \
  --main-class com.drawboard.app.DrawboardApplication \
  --icon src/main/resources/icons/drawboard.icns \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--module-path" \
  --java-options "libs" \
  --java-options "--add-modules" \
  --java-options "javafx.controls,javafx.fxml,javafx.web,javafx.swing" \
  --verbose

echo ""
echo "Package created successfully!"
echo "DMG file location: target/dist/Drawboard-0.1.0.dmg"
