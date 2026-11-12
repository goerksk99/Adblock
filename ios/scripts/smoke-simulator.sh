#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(uname -s)" == Darwin ]] || { echo 'Der Apple-Simulator benötigt macOS.' >&2; exit 1; }
[[ -d AdShield.xcodeproj ]] || xcodegen generate
xcodebuild -project AdShield.xcodeproj -scheme AdShield -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build/simulator CODE_SIGNING_ALLOWED=NO build
ADSHIELD_SIMULATOR_ID="$(xcrun simctl list devices available --json | python3 -c 'import json,sys; d=json.load(sys.stdin); devices=[x for runtime,items in d["devices"].items() if "iOS" in runtime for x in items if x.get("isAvailable") and x["name"].startswith("iPhone")]; assert devices,"Kein verfügbarer iPhone-Simulator"; print(next((x for x in devices if x["state"]=="Booted"),devices[0])["udid"])')"
ADSHIELD_ALREADY_BOOTED="$(xcrun simctl list devices --json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(next(x["state"] for items in d["devices"].values() for x in items if x["udid"]==sys.argv[1]))' "$ADSHIELD_SIMULATOR_ID")"
if [[ "$ADSHIELD_ALREADY_BOOTED" != Booted ]]; then
  xcrun simctl boot "$ADSHIELD_SIMULATOR_ID"
  trap 'xcrun simctl shutdown "$ADSHIELD_SIMULATOR_ID" >/dev/null 2>&1 || true' EXIT
fi
xcrun simctl bootstatus "$ADSHIELD_SIMULATOR_ID" -b
xcrun simctl install "$ADSHIELD_SIMULATOR_ID" build/simulator/Build/Products/Debug-iphonesimulator/AdShield.app
xcrun simctl launch "$ADSHIELD_SIMULATOR_ID" de.adshield.ios
sleep 3
xcrun simctl io "$ADSHIELD_SIMULATOR_ID" screenshot build/simulator-overview.png
printf '%s\n' 'Simulator-Start und Screenshot abgeschlossen. Dies prüft keinen echten DNS-Tunnel.'
