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
if [ ! -z "$SUPABASE_API_KEY" ]; then
  echo "SUPABASE_API_KEY=$SUPABASE_API_KEY" >> local.properties
  echo "Added SUPABASE_API_KEY to local.properties"
fi

# Add Supabase key if it exists in the environment
if [ ! -z "$TEST_USER_MAIL" ]; then
  echo "TEST_USER_MAIL=$TEST_USER_MAIL" >> local.properties
  echo "Added TEST_USER_MAIL to local.properties"
fi

# Add Supabase key if it exists in the environment
if [ ! -z "$TEST_USER_PASSWORD" ]; then
  echo "TEST_USER_PASSWORD=$TEST_USER_PASSWORD" >> local.properties
  echo "Added TEST_USER_PASSWORD to local.properties"
fi

echo "local.properties updated successfully"
