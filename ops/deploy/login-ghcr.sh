#!/usr/bin/env bash
set -euo pipefail
set +x
[[ $EUID -eq 0 ]] || { echo 'Run with sudo bash /opt/sebu-deploy/login-ghcr.sh'; exit 1; }
umask 077
read -r -p 'GitHub username with package read access: ' ghcr_user
[[ "$ghcr_user" =~ ^[A-Za-z0-9-]+$ ]] || { echo 'Invalid GitHub username'; exit 1; }
read -r -s -p 'PAT (classic), read:packages only (hidden input): ' ghcr_token
printf '\n'
[[ -n "$ghcr_token" ]] || { echo 'No token entered'; exit 1; }
trap 'unset ghcr_token' EXIT
printf '%s' "$ghcr_token" | docker login ghcr.io --username "$ghcr_user" --password-stdin
unset ghcr_token
# Without a credential helper Docker stores reversible credentials in this file.
# The agent runs as root, so keep it inaccessible to other users.
if [[ -f /root/.docker/config.json && ! -L /root/.docker/config.json ]]; then
  chmod 0600 /root/.docker/config.json
fi
echo 'Private registry login complete. Never share the token or Docker config.json.'
