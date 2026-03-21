#!/bin/bash

# Script to update local.properties with GitHub secrets
echo "Updating local.properties with GitHub secrets..."

# Create local.properties if it doesn't exist
touch local.properties

if [ ! -z "$ANDROID_SDK_ROOT" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
fi

if [ ! -z "$SERVER_URL" ]; then
  echo "SERVER_URL=$SERVER_URL" >> local.properties
  echo "Added SERVER_URL to local.properties"
fi

echo "local.properties updated successfully"
