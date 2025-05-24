#!/usr/bin/env bash

# Check if we are on master branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "master" ]; then
  # Not on master branch, exit successfully without checks
  exit 0
fi

set -e
./gradlew ktfmtCheck desktopTest connectedAndroidTest
set +e
