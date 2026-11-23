#!/bin/bash
# Builds cozy-rabbit-study-windows.zip (64-bit Windows 10/11 with an Intel or AMD processor) on the Desktop.
# Friends need Java 21+ installed; see packaging/windows/README-WINDOWS.txt.
set -euo pipefail
cd "$(dirname "$0")/.."

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"
fi
VERSION="1.0.0"
# JavaFX 21 runs on Java 21 and newer. It only ships a 64-bit Intel/AMD Windows build ("win").
FX="21.0.13"
OUT="dist/cozy-rabbit-study-windows"
TMP="dist/tmp-win"

echo "1/3  Building the jar and the Windows libraries..."
rm -rf "$OUT" "$TMP" "dist/cozy-rabbit-study-windows.zip"
mkdir -p "$OUT/lib/common" "$OUT/lib/win"
"$MVN" -q clean package -Djavafx.version="$FX"
cp "target/cozy-rabbit-study-${VERSION}.jar" "$OUT/lib/common/"
# -Djavafx.platform=win makes Maven fetch the Windows builds of JavaFX instead of the Mac ones
"$MVN" -q dependency:copy-dependencies -DincludeScope=runtime -Djavafx.platform=win \
    -Djavafx.version="$FX" -DoutputDirectory="$TMP"
mv "$TMP"/javafx-*-win.jar "$OUT/lib/win/"
# sqlite-jdbc contains the native library for every platform, Windows included
cp "$TMP"/sqlite-jdbc-*.jar "$OUT/lib/common/"
rm -rf "$TMP"

echo "2/3  Adding the launcher, installer and icon..."
# Windows text files get Windows line endings (CRLF)
for f in start.bat install.bat install.ps1 README-WINDOWS.txt; do
  perl -pe 's/\r?\n/\r\n/' "packaging/windows/$f" > "$OUT/$f"
done
java -Djava.awt.headless=true -cp target/classes com.cozyrabbit.tools.IcoWriter A "$OUT/icon.ico"

echo "3/3  Zipping..."
(cd dist && zip -qr cozy-rabbit-study-windows.zip cozy-rabbit-study-windows)
cp dist/cozy-rabbit-study-windows.zip "$HOME/Desktop/"
echo "Done: $HOME/Desktop/cozy-rabbit-study-windows.zip"
# put the normal (Mac) build of the classes back for running from IntelliJ
"$MVN" -q compile
