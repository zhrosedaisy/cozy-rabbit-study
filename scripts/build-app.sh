#!/bin/bash
# Builds "Cozy Rabbit Study.app" (with its own Java runtime and the rabbit icon)
# and copies it to the Desktop. Run from anywhere:  ./scripts/build-app.sh
set -euo pipefail
cd "$(dirname "$0")/.."

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"
fi

APP_NAME="Cozy Rabbit Study"
VERSION="1.0.0"

echo "1/4  Building the jar and collecting libraries..."
rm -rf target/lib target/icon.iconset target/CozyRabbit.icns dist
"$MVN" -q package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib
cp "target/cozy-rabbit-study-${VERSION}.jar" target/lib/

echo "2/4  Drawing the icon..."
CP="target/classes:$(ls target/lib/*.jar | tr '\n' ':')"
java -Djava.awt.headless=true -cp "$CP" com.cozyrabbit.tools.AppIcon target/icon-preview A
java -Djava.awt.headless=true -cp "$CP" com.cozyrabbit.tools.IconsetWriter A target/icon.iconset
iconutil -c icns target/icon.iconset -o target/CozyRabbit.icns

echo "3/4  Packaging the app..."
jpackage --type app-image \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --input target/lib \
  --main-jar "cozy-rabbit-study-${VERSION}.jar" \
  --main-class com.cozyrabbit.Launcher \
  --icon target/CozyRabbit.icns \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --mac-package-identifier com.cozyrabbit.study \
  --dest dist

# Study rooms talk to friends on the same Wi-Fi; macOS asks for "Local Network" access with this text.
PLIST="dist/$APP_NAME.app/Contents/Info.plist"
plutil -insert NSLocalNetworkUsageDescription -string \
  "Cozy Rabbit Study finds and joins study rooms of your friends on the same Wi-Fi." "$PLIST"
codesign --force --deep --sign - "dist/$APP_NAME.app" >/dev/null 2>&1 || true

echo "4/4  Copying to the Desktop..."
rm -rf "$HOME/Desktop/$APP_NAME.app"
cp -R "dist/$APP_NAME.app" "$HOME/Desktop/"

# Finder caches app icons by path, so a rebuilt app at the same place can show the generic icon.
# Re-register the app and pin its icon explicitly so the rabbit always shows.
APP="$HOME/Desktop/$APP_NAME.app"
ICNS="$APP/Contents/Resources/$APP_NAME.icns"
touch "$APP"
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$APP" || true
osascript -l JavaScript -e "ObjC.import('AppKit'); \$.NSWorkspace.sharedWorkspace.setIconForFileOptions(\$.NSImage.alloc.initWithContentsOfFile('$ICNS'), '$APP', 0);" >/dev/null || true
echo "Done: $HOME/Desktop/$APP_NAME.app"
