#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Der iOS-App-Build benötigt macOS und Xcode. Den portablen Kern mit swift test prüfen.' >&2; exit 1
fi
command -v xcodegen >/dev/null || { echo 'Bitte XcodeGen installieren: brew install xcodegen' >&2; exit 1; }
xcodegen generate
swift test
xcodebuild -project AdShield.xcodeproj -scheme AdShield -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
