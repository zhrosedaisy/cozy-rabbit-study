#!/usr/bin/env bash
# Adds Cozy Rabbit Study to the app menu (with the rabbit icon). Run once from the unzipped folder.
set -e
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
chmod +x "$DIR/start.sh"
APPS="$HOME/.local/share/applications"
mkdir -p "$APPS"
cat > "$APPS/cozy-rabbit-study.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Cozy Rabbit Study
Comment=A cozy study timer with a fishing rabbit
Exec="$DIR/start.sh"
Icon=$DIR/icon.png
Terminal=false
Categories=Education;Utility;
StartupWMClass=com.cozyrabbit.Launcher
DESKTOP
chmod +x "$APPS/cozy-rabbit-study.desktop"
if [ -d "$HOME/Desktop" ]; then
  cp "$APPS/cozy-rabbit-study.desktop" "$HOME/Desktop/"
  chmod +x "$HOME/Desktop/cozy-rabbit-study.desktop"
  # GNOME (Ubuntu) asks to trust desktop launchers; mark it trusted when possible
  command -v gio >/dev/null 2>&1 && gio set "$HOME/Desktop/cozy-rabbit-study.desktop" metadata::trusted true 2>/dev/null || true
fi
update-desktop-database "$APPS" >/dev/null 2>&1 || true
echo "Done. Look for \"Cozy Rabbit Study\" in your app menu."
