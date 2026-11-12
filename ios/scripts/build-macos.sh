#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Der iOS-App-Build benötigt macOS und Xcode. Den portablen Kern mit swift test prüfen.' >&2; exit 1
fi
command -v xcodegen >/dev/null || { echo 'Bitte XcodeGen installieren: brew install xcodegen' >&2; exit 1; }
xcodebuild -version
xcodegen generate
swift test
swift test -c release
xcodebuild -project AdShield.xcodeproj -scheme AdShield -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -archivePath build/AdShield.xcarchive -derivedDataPath build/device CODE_SIGNING_ALLOWED=NO archive
