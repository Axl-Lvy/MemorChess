#!/bin/bash

# Script to update local.properties with GitHub secrets
echo "Updating local.properties with GitHub secrets..."

# Create local.properties if it doesn't exist
touch local.properties

if [ ! -z "$ANDROID_SDK_ROOT" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
fi

if [ ! -z "$SUPABASE_API_KEY" ]; then
  echo "SUPABASE_API_KEY=$SUPABASE_API_KEY" >> local.properties
  echo "Added SUPABASE_API_KEY to local.properties"
fi

if [ ! -z "$TEST_USER_MAIL" ]; then
  echo "TEST_USER_MAIL=$TEST_USER_MAIL" >> local.properties
  echo "Added TEST_USER_MAIL to local.properties"
fi

if [ ! -z "$TEST_USER_PASSWORD" ]; then
  echo "TEST_USER_PASSWORD=$TEST_USER_PASSWORD" >> local.properties
  echo "Added TEST_USER_PASSWORD to local.properties"
fi

if [ ! -z "$SUPABASE_DB_HOST" ]; then
  echo ".supabase_db_host=$SUPABASE_DB_HOST" >> local.properties
  echo "Added SUPABASE_DB_HOST to local.properties"
fi

if [ ! -z "$SUPABASE_DB_PORT" ]; then
  echo ".supabase_db_port=$SUPABASE_DB_PORT" >> local.properties
  echo "Added SUPABASE_DB_PORT to local.properties"
fi

if [ ! -z "$SUPABASE_DB_NAME" ]; then
  echo ".supabase_db_name=$SUPABASE_DB_NAME" >> local.properties
  echo "Added SUPABASE_DB_NAME to local.properties"
fi

if [ ! -z "$SUPABASE_DB_USER" ]; then
  echo ".supabase_db_user=$SUPABASE_DB_USER" >> local.properties
  echo "Added SUPABASE_DB_USER to local.properties"
fi

if [ ! -z "$SUPABASE_DB_PASSWORD" ]; then
  echo ".supabase_db_password=$SUPABASE_DB_PASSWORD" >> local.properties
  echo "Added SUPABASE_DB_PASSWORD to local.properties"
fi

echo "local.properties updated successfully"
