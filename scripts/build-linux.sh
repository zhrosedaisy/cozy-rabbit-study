#!/bin/bash
# Builds cozy-rabbit-study-linux.zip (for Ubuntu, Fedora and other x86_64 Linux) and puts it on the Desktop.
# Friends need Java 21+ installed; see packaging/linux/README-LINUX.txt.
set -euo pipefail
cd "$(dirname "$0")/.."

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn"
fi
VERSION="1.0.0"
# JavaFX 21 is the long-term line that runs on Java 21, which Ubuntu and Fedora ship as a package.
# (The Mac app bundles its own newer Java, so it can use the newest JavaFX.)
FX="21.0.13"
OUT="dist/cozy-rabbit-study-linux"

echo "1/3  Building the jar and the Linux libraries..."
rm -rf "$OUT" "dist/cozy-rabbit-study-linux.zip"
mkdir -p "$OUT/lib/common" "$OUT/lib/x86_64" "$OUT/lib/aarch64"
"$MVN" -q clean package -Djavafx.version="$FX"
cp "target/cozy-rabbit-study-${VERSION}.jar" "$OUT/lib/common/"
# JavaFX contains native code, so there is one build per processor type:
#   x86_64  = normal Intel/AMD PCs,  aarch64 = ARM (e.g. Fedora Asahi on Apple-chip Macs).
# start.sh picks the right folder with `uname -m`.
for ARCH in x86_64 aarch64; do
  PLATFORM="linux"
  [ "$ARCH" = "aarch64" ] && PLATFORM="linux-aarch64"
  TMP="dist/tmp-$ARCH"
  rm -rf "$TMP"
  "$MVN" -q dependency:copy-dependencies -DincludeScope=runtime -Djavafx.platform="$PLATFORM" \
      -Djavafx.version="$FX" -DoutputDirectory="$TMP"
  mv "$TMP"/javafx-*-"$PLATFORM".jar "$OUT/lib/$ARCH/"
  # the plain javafx-*.jar files are empty placeholders and sqlite-jdbc covers every platform
  cp -n "$TMP"/sqlite-jdbc-*.jar "$OUT/lib/common/" 2>/dev/null || true
  rm -rf "$TMP"
done

echo "2/3  Adding the launcher, installer and icon..."
cp packaging/linux/start.sh packaging/linux/install.sh packaging/linux/README-LINUX.txt "$OUT/"
chmod +x "$OUT/start.sh" "$OUT/install.sh"
java -Djava.awt.headless=true -cp "target/classes" com.cozyrabbit.tools.AppIcon "$OUT" A
mv "$OUT/icon-A.png" "$OUT/icon.png"

echo "3/3  Zipping..."
(cd dist && zip -qr cozy-rabbit-study-linux.zip cozy-rabbit-study-linux)
cp dist/cozy-rabbit-study-linux.zip "$HOME/Desktop/"
echo "Done: $HOME/Desktop/cozy-rabbit-study-linux.zip"
# put the Mac build of the classes back for running from IntelliJ
"$MVN" -q compile
