#!/usr/bin/env bash
# Pulls and restarts just the server container. Called by webhook-hooks.yaml on a valid deploy
# request; safe to run by hand too.
set -euo pipefail
cd "$(dirname "$0")"
docker compose pull server
docker compose up -d server
