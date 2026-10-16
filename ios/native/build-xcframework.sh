#!/bin/sh
# Builds DecivNativeUI.xcframework (device + simulator) for RoboVM to link and embed.
# Usage: ios/native/build-xcframework.sh   (from anywhere)
set -eu

here="$(cd "$(dirname "$0")" && pwd)"
build="$here/build"
output="$build/DecivNativeUI.xcframework"

cd "$here"
xcodegen generate --quiet

rm -rf "$build/archives" "$output"
for destination in "generic/platform=iOS" "generic/platform=iOS Simulator"; do
    case "$destination" in
        *Simulator) archive="$build/archives/simulator.xcarchive" ;;
        *) archive="$build/archives/device.xcarchive" ;;
    esac
    xcodebuild archive \
        -project DecivNativeUI.xcodeproj \
        -scheme DecivNativeUI \
        -configuration Release \
        -destination "$destination" \
        -archivePath "$archive" \
        -derivedDataPath "$build/DerivedData" \
        SKIP_INSTALL=NO \
        -quiet
done

# The framework is only reached through the Objective-C runtime, so no swiftinterface is needed.
xcodebuild -create-xcframework -allow-internal-distribution \
    -framework "$build/archives/device.xcarchive/Products/Library/Frameworks/DecivNativeUI.framework" \
    -framework "$build/archives/simulator.xcarchive/Products/Library/Frameworks/DecivNativeUI.framework" \
    -output "$output"

echo "Built $output"
