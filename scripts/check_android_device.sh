#!/usr/bin/env bash
# Run in the GitHub-hosted emulator; preserve screenshots even on test failure.
set -u
cd "$(dirname "$0")/../android" || exit 1
native_args=()
if [[ -n "${ANDROID_TEST_CLASS:-}" ]]; then
 [[ "$ANDROID_TEST_CLASS" =~ ^[A-Za-z0-9_.\$,#]+$ ]] || exit 1
 native_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$ANDROID_TEST_CLASS")
fi
gradle connectedDebugAndroidTest --no-daemon "${native_args[@]}"
native_test_result=$?
adb pull /sdcard/Download/RachelsPractice app/build/native-screenshots || true
mkdir -p app/build/native-screenshots
adb logcat -d -s CoursePlayback:W > app/build/native-screenshots/course-playback.log || true
exit "$native_test_result"
