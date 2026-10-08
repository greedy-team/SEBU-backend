#!/usr/bin/env bash
# Installs the pull agent, but does NOT start deployments or restart any container.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo bash ops/deploy/install.sh [develop|main]'; exit 1; }
[[ $# -le 1 ]] || { echo 'Usage: install.sh [develop|main]'; exit 1; }
channel=${1:-develop}
case "$channel" in
  develop) config_example=config.example.json ;;
  main) config_example=config.main.example.json ;;
  *) echo 'Only develop or main may be installed'; exit 1 ;;
esac
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for command in python3 docker systemctl install; do
  command -v "$command" >/dev/null || { echo "Missing dependency: $command"; exit 1; }
done
if [[ "$channel" == main ]] && { [[ ! -f /etc/sebu-deploy/config.json ]] || [[ ! -f /etc/sebu-deploy/backend.env ]]; }; then
  echo 'Prepare production config.json and backend.env from the main examples first (root-owned, mode 0600).'
  echo 'Set the real HTTPS API URL and independent DB/JWT secrets. Bootstrap MySQL and a healthy backend before installing the pull agent.'
  exit 1
fi
# Do not turn a development host into a production host by changing the tag.
# Production must have its own DB, secrets, and reviewed initial backend container.
if [[ -e /etc/sebu-deploy/config.json ]]; then
  configured_channel=$(python3 -c 'import json; print(json.load(open("/etc/sebu-deploy/config.json"))["tag"])')
  [[ "$configured_channel" == "$channel" ]] || {
    echo 'Existing configuration belongs to another channel. Use a separate host; no files changed.'
    exit 1
  }
fi
for directory in /opt/sebu-deploy /etc/sebu-deploy /var/lib/sebu-deploy; do
  [[ ! -L "$directory" ]] || { echo "Refusing symlink: $directory"; exit 1; }
  install -d -o root -g root -m 0700 "$directory"
done
# Updating a running deployment agent requires an explicit maintenance step.
if systemctl is-active --quiet sebu-pull-deploy.service || [[ -e /etc/sebu-deploy/enabled ]]; then
  echo 'Disable the timer, remove the enable marker, and wait for the service to finish before installing.'
  exit 1
fi
install -o root -g root -m 0700 "$script_dir/deploy.py" /opt/sebu-deploy/deploy.py
install -o root -g root -m 0700 "$script_dir/login-ghcr.sh" /opt/sebu-deploy/login-ghcr.sh
if [[ ! -e /etc/sebu-deploy/config.json ]]; then
  install -o root -g root -m 0600 "$script_dir/$config_example" /etc/sebu-deploy/config.json
fi
if [[ ! -e /etc/sebu-deploy/backend.env ]]; then
  python3 /opt/sebu-deploy/deploy.py capture-env
fi
install -o root -g root -m 0644 "$script_dir/sebu-pull-deploy.service" /etc/systemd/system/sebu-pull-deploy.service
install -o root -g root -m 0644 "$script_dir/sebu-pull-deploy.timer" /etc/systemd/system/sebu-pull-deploy.timer
systemctl daemon-reload
echo "Installed $channel agent. Timer NOT enabled. Existing backend, Caddy and MySQL were not restarted."
echo 'Review config.json (including the real public API URL) and backend.env on this host before check/deploy.'
echo 'Next: private GHCR login, check, reviewed first deployment, then enable timer. See runbook.'
