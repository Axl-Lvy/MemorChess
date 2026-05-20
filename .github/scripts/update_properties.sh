#!/bin/bash

# Script to update local.properties with environment values.
echo "Updating local.properties..."

# Create local.properties if it doesn't exist
touch local.properties

if [ ! -z "$ANDROID_SDK_ROOT" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
fi

echo "local.properties updated successfully"
