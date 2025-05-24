#!/usr/bin/env bash

# Script to copy git hooks to .git/hooks directory

# Get the root directory of the git repository
ROOT_DIR=$(git rev-parse --show-toplevel)

# Create hooks directory if it doesn't exist
mkdir -p "$ROOT_DIR/.git/hooks"

# Copy hooks
cp "$ROOT_DIR/ci/hooks/pre-commit.sh" "$ROOT_DIR/.git/hooks/pre-commit"
cp "$ROOT_DIR/ci/hooks/commit-msg.sh" "$ROOT_DIR/.git/hooks/commit-msg"

# Make hooks executable
chmod +x "$ROOT_DIR/.git/hooks/pre-commit"
chmod +x "$ROOT_DIR/.git/hooks/commit-msg"

echo "Git hooks installed successfully."