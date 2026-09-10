#!/usr/bin/env bash
# Installs the pull agent, but does NOT start deployments or restart any container.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo bash ops/deploy/install.sh'; exit 1; }
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for command in python3 docker systemctl install; do
  command -v "$command" >/dev/null || { echo "Missing dependency: $command"; exit 1; }
done
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
  install -o root -g root -m 0600 "$script_dir/config.example.json" /etc/sebu-deploy/config.json
fi
if [[ ! -e /etc/sebu-deploy/backend.env ]]; then
  python3 /opt/sebu-deploy/deploy.py capture-env
fi
install -o root -g root -m 0644 "$script_dir/sebu-pull-deploy.service" /etc/systemd/system/sebu-pull-deploy.service
install -o root -g root -m 0644 "$script_dir/sebu-pull-deploy.timer" /etc/systemd/system/sebu-pull-deploy.timer
systemctl daemon-reload
echo 'Installed. Timer NOT enabled. Existing backend, Caddy and MySQL were not restarted.'
echo 'Next: private GHCR login, check, reviewed first deployment, then enable timer. See runbook.'
