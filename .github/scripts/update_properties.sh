#!/bin/bash

# Script to update local.properties with GitHub secrets
echo "Updating local.properties with GitHub secrets..."

# Create local.properties if it doesn't exist
touch local.properties

# Add SDK directory if it exists in the environment
if [ ! -z "$ANDROID_SDK_ROOT" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
fi

# Add Supabase key if it exists in the environment
if [ ! -z "$SUPABASE_ANON_KEY" ]; then
  echo "SUPABASE_ANON_KEY=$SUPABASE_ANON_KEY" >> local.properties
  echo "Added SUPABASE_ANON_KEY to local.properties"
fi

# Add any other secrets as needed
# if [ ! -z "$OTHER_SECRET" ]; then
#   echo "OTHER_SECRET=$OTHER_SECRET" >> local.properties
#   echo "Added OTHER_SECRET to local.properties"
# fi

echo "local.properties updated successfully"