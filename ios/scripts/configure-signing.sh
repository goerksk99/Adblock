#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# != 2 ]]; then
  echo 'Aufruf: ./scripts/configure-signing.sh TEAM_ID eindeutige.bundle.id' >&2; exit 1
fi
if [[ ! "$1" =~ ^[A-Z0-9]{10}$ ]]; then
  echo 'Die Apple Team-ID besteht aus 10 Großbuchstaben/Ziffern; sie ist kein Passwort.' >&2; exit 1
fi
if [[ ! "$2" =~ ^[A-Za-z][A-Za-z0-9-]*(\.[A-Za-z][A-Za-z0-9-]*)+$ ]]; then
  echo 'Bitte eine gültige Bundle-ID verwenden, zum Beispiel de.goerksk99.adshield.' >&2; exit 1
fi
mkdir -p Config
printf 'ADSHIELD_TEAM_ID = %s\nADSHIELD_BUNDLE_PREFIX = %s\n' "$1" "$2" > Config/Signing.local.xcconfig
printf '%s\n' 'Lokale Team- und Bundle-ID gespeichert. In Xcode für beide Targets die gemeinsame App-Gruppe und Network Extension aktivieren.'
