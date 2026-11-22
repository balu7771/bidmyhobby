#!/usr/bin/env bash
set -euo pipefail

echo "==> Setting up directories..."
install -d -m 0755 /opt/bidmyhobby/services/api-gateway /opt/bidmyhobby/deployment/gcp /opt/bidmyhobby/deployment/n8n

echo "==> Installing configuration files..."
install -m 0644 /tmp/docker-compose.gcp.yml /opt/bidmyhobby/docker-compose.gcp.yml
install -m 0644 /tmp/nginx.conf /opt/bidmyhobby/services/api-gateway/nginx.conf
install -m 0644 /tmp/Caddyfile /opt/bidmyhobby/deployment/gcp/Caddyfile
[ -f /tmp/bidmyhobby-email-workflow.json ] && install -m 0644 /tmp/bidmyhobby-email-workflow.json /opt/bidmyhobby/deployment/n8n/bidmyhobby-email-workflow.json

echo "==> Updating environment configuration and removing duplicates..."
python3 - << 'EOF'
import os
import sys

env_file = "/opt/bidmyhobby/.env"
deploy_file = "/tmp/deploy.env"

config = {}
keys_order = []

if os.path.exists(env_file):
    with open(env_file, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "=" in stripped:
                k, v = stripped.split("=", 1)
                k = k.strip()
                v = v.strip()
                if k not in config:
                    keys_order.append(k)
                config[k] = v

if os.path.exists(deploy_file):
    with open(deploy_file, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "=" in stripped:
                k, v = stripped.split("=", 1)
                k = k.strip()
                v = v.strip()
                if v:
                    if k not in config:
                        keys_order.append(k)
                    config[k] = v

standard_vars = ["IMAGE_BASE", "IMAGE_TAG", "GCP_DOMAIN", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "N8N_WEBHOOK_URL"]
for var in standard_vars:
    val = os.environ.get(var)
    if val and val.strip():
        if var not in config:
            keys_order.append(var)
        config[var] = val.strip()

if "N8N_WEBHOOK_URL" not in config:
    keys_order.append("N8N_WEBHOOK_URL")
    config["N8N_WEBHOOK_URL"] = "http://n8n:5678/webhook/send-email"

with open(env_file, "w", encoding="utf-8") as f:
    for k in keys_order:
        f.write(f"{k}={config[k]}\n")

if os.path.exists(deploy_file):
    try:
        os.remove(deploy_file)
    except OSError:
        pass

print(f"Updated and deduplicated {env_file} successfully ({len(keys_order)} variables).")
EOF

chmod 0644 /opt/bidmyhobby/.env

echo "==> Pulling container images..."
docker compose --env-file /opt/bidmyhobby/.env -f /opt/bidmyhobby/docker-compose.gcp.yml pull

echo "==> Starting containers..."
docker compose --env-file /opt/bidmyhobby/.env -f /opt/bidmyhobby/docker-compose.gcp.yml up -d --wait --wait-timeout 480 --remove-orphans

echo "==> Restarting Caddy for latest certificates and routing..."
docker compose --env-file /opt/bidmyhobby/.env -f /opt/bidmyhobby/docker-compose.gcp.yml restart caddy

echo "==> Container status:"
docker compose --env-file /opt/bidmyhobby/.env -f /opt/bidmyhobby/docker-compose.gcp.yml ps

echo "==> Deployment complete!"
