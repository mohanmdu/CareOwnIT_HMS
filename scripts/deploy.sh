#!/usr/bin/env bash
# Manual deploy - bypasses GitHub Actions entirely, using the same SSH path
# already proven to work reliably (unlike the GitHub-hosted runner, which
# currently times out reaching the VPS - see the "Cloud Firewall" note in
# the repo README/deploy docs). Builds locally, uploads, then runs the
# exact same on-VPS deploy-*.sh scripts the CI pipeline itself calls, so
# behavior (timestamped releases, health-check, auto-rollback) is identical.
#
# Requires: an SSH config entry named `careown-vps-deploy` (or override via
# DEPLOY_SSH_HOST below) with a key already authorized for the VPS's
# `deploy` user, which has passwordless sudo scoped to these scripts.
#
# Usage:
#   scripts/deploy.sh api        # build + deploy hms-api only
#   scripts/deploy.sh web        # build + deploy hms-web only
#   scripts/deploy.sh website    # build + deploy hms-website only
#   scripts/deploy.sh all        # all three (default if no argument given)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_HOST="${DEPLOY_SSH_HOST:-careown-vps-deploy}"
TARGET="${1:-all}"

log() { echo "[deploy] $*"; }

deploy_api() {
  log "Building hms-api..."
  (cd "$REPO_ROOT/hms-api" && ./mvnw -q -o clean package -DskipTests)
  local jar="$REPO_ROOT/hms-api/target/hms-api-0.0.1-SNAPSHOT.jar"
  local remote_tmp="/home/deploy/hms-api-deploy-$(date +%s).jar"
  log "Uploading jar..."
  scp -q "$jar" "$SSH_HOST:$remote_tmp"
  log "Running deploy-hms-api.sh on the VPS..."
  ssh "$SSH_HOST" "sudo /home/CareOwn_HMS/scripts/deploy-hms-api.sh $remote_tmp && rm -f $remote_tmp"
  log "hms-api deployed."
}

deploy_web() {
  log "Building hms-web (production)..."
  (cd "$REPO_ROOT/hms-web" && npm ci --silent && npx ng build --configuration production)
  local dist_dir="$REPO_ROOT/hms-web/dist/hms-web/browser"
  local remote_tmp="/home/deploy/hms-ui-deploy-$(date +%s)"
  log "Uploading build..."
  ssh "$SSH_HOST" "mkdir -p $remote_tmp"
  scp -q -r "$dist_dir" "$SSH_HOST:$remote_tmp/browser"
  log "Running deploy-hms-ui.sh on the VPS..."
  ssh "$SSH_HOST" "sudo /home/CareOwn_HMS/scripts/deploy-hms-ui.sh $remote_tmp/browser && rm -rf $remote_tmp"
  log "hms-web deployed."
}

deploy_website() {
  log "Building hms-website..."
  (cd "$REPO_ROOT/hms-website" && npm ci --silent && npm run build)
  local dist_dir="$REPO_ROOT/hms-website/dist/hms-website"
  local remote_tmp="/home/deploy/hms-website-deploy-$(date +%s)"
  log "Uploading build..."
  scp -q -r "$dist_dir" "$SSH_HOST:$remote_tmp"
  log "Running deploy-hms-website.sh on the VPS..."
  ssh "$SSH_HOST" "sudo /home/CareOwn_HMS/scripts/deploy-hms-website.sh $remote_tmp && rm -rf $remote_tmp"
  log "hms-website deployed."
}

case "$TARGET" in
  api) deploy_api ;;
  web) deploy_web ;;
  website) deploy_website ;;
  all) deploy_api; deploy_web; deploy_website ;;
  *) echo "Usage: $0 [api|web|website|all]" >&2; exit 1 ;;
esac

log "Done."
