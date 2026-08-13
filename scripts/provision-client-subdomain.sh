#!/usr/bin/env bash
set -euo pipefail

# Provisions the nginx server block + TLS certificate for a client's public
# hms-website subdomain (e.g. abc.careownitsolutions.com). Run on the VPS,
# after the DNS A record already exists and points here (certbot's HTTP-01
# challenge needs it to resolve first) and before setting the client's
# Domain field in Super Admin.
#
# Usage: sudo ./provision-client-subdomain.sh <subdomain>

SUBDOMAIN="${1:?Usage: provision-client-subdomain.sh <subdomain>}"
HMS_WEBSITE_PORT=4000
CERTBOT_EMAIL="mohanraj2014@gmail.com"

CONF="/etc/nginx/sites-available/${SUBDOMAIN}.conf"

cat > "$CONF" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${SUBDOMAIN};

    location / {
        proxy_pass http://127.0.0.1:${HMS_WEBSITE_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOF

ln -sf "$CONF" "/etc/nginx/sites-enabled/${SUBDOMAIN}.conf"
nginx -t
systemctl reload nginx

certbot --nginx -d "${SUBDOMAIN}" --non-interactive --agree-tos -m "${CERTBOT_EMAIL}" --redirect

curl -s -o /dev/null -w "verify: HTTP %{http_code}\n" "https://${SUBDOMAIN}/"
