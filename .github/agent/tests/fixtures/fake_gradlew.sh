#!/bin/sh
# Stands in for ./gradlew in tests: records its arguments, copies canned JUnit XML into every
# configured results dir, and fails when FAKE_GRADLE_FAIL names one of the requested tasks.
echo "$@" >> "${FAKE_GRADLE_LOG:-/dev/null}"
for task in "$@"; do
  if [ -n "$FAKE_GRADLE_FAIL" ] && [ "$task" = "$FAKE_GRADLE_FAIL" ]; then
    echo "e: Board.kt:3:1 Unresolved reference: nope"; exit 1
  fi
done
if [ -n "$FAKE_JUNIT_SRC" ]; then
  for d in composeApp/build/test-results/jvmTest shared/build/test-results/jvmTest; do
    mkdir -p "$d" && cp "$FAKE_JUNIT_SRC"/*.xml "$d"/
  done
fi
exit 0
