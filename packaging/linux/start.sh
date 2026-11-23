#!/usr/bin/env bash
# Starts Cozy Rabbit Study. Needs Java 21 or newer (see README-LINUX.txt).
cd "$(dirname "$(readlink -f "$0")")" || exit 1

if ! command -v java >/dev/null 2>&1; then
  msg="Java is not installed. Ubuntu: sudo apt install openjdk-21-jre   Fedora: sudo dnf install java-25-openjdk"
  echo "$msg"
  command -v notify-send >/dev/null 2>&1 && notify-send "Cozy Rabbit Study" "$msg"
  exit 1
fi

version=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
if [ "${version:-0}" -lt 21 ] 2>/dev/null; then
  msg="Cozy Rabbit Study needs Java 21 or newer (you have $version). Ubuntu: sudo apt install openjdk-21-jre   Fedora: sudo dnf install java-25-openjdk"
  echo "$msg"
  command -v notify-send >/dev/null 2>&1 && notify-send "Cozy Rabbit Study" "$msg"
  exit 1
fi

extra=""
if [ "$version" -ge 22 ]; then
  extra="--enable-native-access=ALL-UNNAMED"
fi
case "$(uname -m)" in
  aarch64|arm64) arch=aarch64 ;;
  x86_64|amd64)  arch=x86_64 ;;
  *) echo "Sorry, this processor type ($(uname -m)) is not supported yet."; exit 1 ;;
esac

exec java $extra -cp "lib/common/*:lib/$arch/*" com.cozyrabbit.Launcher "$@"
