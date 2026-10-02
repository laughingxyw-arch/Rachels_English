#!/usr/bin/env bash
# Run in the GitHub-hosted emulator; preserve screenshots even on test failure.
set -u
cd "$(dirname "$0")/../android" || exit 1
gradle connectedDebugAndroidTest --no-daemon
native_test_result=$?
adb pull /sdcard/Android/data/com.rachelsenglish.practice/files/screenshots app/build/native-screenshots || true
exit "$native_test_result"
