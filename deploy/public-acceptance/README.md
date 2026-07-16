# RPB protected public acceptance deployment package

## Purpose

This package prepares a protected public acceptance environment for RPB so stakeholders can validate the web app from the internet before production security hardening is complete.

This is not a production deployment package. No real business data, real tenant data, production credentials, or production media should be used here.

## Included Files

- `env/rpb-backend.env.example`: backend environment template for `/etc/rpb/rpb-backend.env`.
- `nginx/rpb-public-acceptance.conf`: Nginx site template with Basic Auth, IP allowlist, static frontend hosting, and `/api` reverse proxy.
- `systemd/rpb-backend.service`: systemd unit for the Spring Boot backend.
- `scripts/smoke-check.sh`: remote smoke checks for the protected front door, frontend, backend auth, captcha, and optional tenant logo media.

## Server Baseline

- Linux VM with a public IP.
- Domain or temporary DNS name pointed to that VM.
- Java 21.
- Node.js 20+ on the build machine, not necessarily on the server.
- PostgreSQL 16 recommended for this acceptance environment.
- Nginx with `apache2-utils` or `httpd-tools` for `htpasswd`.
- A non-root Linux user named `rpb`.

## Directory Layout

```text
/opt/rpb/app
/opt/rpb/frontend
/opt/rpb/media
/opt/rpb/logs
/etc/rpb
```

Create the directories:

```bash
sudo useradd --system --home /opt/rpb --shell /usr/sbin/nologin rpb || true
sudo mkdir -p /opt/rpb/app /opt/rpb/frontend /opt/rpb/media /opt/rpb/logs /etc/rpb
sudo chown -R rpb:rpb /opt/rpb
sudo chmod 750 /etc/rpb
```

## Build Artifacts

Run from a clean worktree:

```bash
mvn clean package
npm ci
npm run build
```

Copy artifacts to the server:

```bash
sudo install -o rpb -g rpb -m 0640 target/reservation-platform-0.0.1-SNAPSHOT.jar /opt/rpb/app/reservation-platform.jar
sudo rsync -a --delete dist/ /opt/rpb/frontend/
```

## Acceptance Database

Use an isolated acceptance database. Do not restore production data.

```bash
sudo -u postgres createuser rpb_acceptance_app
sudo -u postgres createdb rpb_acceptance
sudo -u postgres psql -c "alter user rpb_acceptance_app with encrypted password 'replace-with-acceptance-only-password';"
sudo -u postgres psql -c "grant all privileges on database rpb_acceptance to rpb_acceptance_app;"
```

The backend runs Flyway by default for this package. If a migration fails, stop and fix the migration or database setup before exposing the site.

## Backend Environment

Copy the template and replace every placeholder value:

```bash
sudo install -o root -g rpb -m 0640 deploy/public-acceptance/env/rpb-backend.env.example /etc/rpb/rpb-backend.env
sudoedit /etc/rpb/rpb-backend.env
```

Keep `RPB_LOCAL_AUTH_ENABLED=false`. Local runtime auth must not be enabled in any public environment.

## Backend Service

Install and start the backend:

```bash
sudo install -o root -g root -m 0644 deploy/public-acceptance/systemd/rpb-backend.service /etc/systemd/system/rpb-backend.service
sudo systemctl daemon-reload
sudo systemctl enable --now rpb-backend
sudo journalctl -u rpb-backend -f
```

The backend should listen only on `127.0.0.1:8080`, behind Nginx.

## Nginx Protected Front Door

Create a Basic Auth file:

```bash
sudo htpasswd -c /etc/nginx/.rpb-public-acceptance.htpasswd reviewer
```

Copy the Nginx template and replace:

- `acceptance.example.com`
- `allow 203.0.113.10;`
- optional extra reviewer IP ranges

```bash
sudo install -o root -g root -m 0644 deploy/public-acceptance/nginx/rpb-public-acceptance.conf /etc/nginx/sites-available/rpb-public-acceptance.conf
sudo ln -sfn /etc/nginx/sites-available/rpb-public-acceptance.conf /etc/nginx/sites-enabled/rpb-public-acceptance.conf
sudo nginx -t
sudo systemctl reload nginx
```

This package requires both Basic Auth and IP allowlist. If the reviewer IP changes often, use Cloudflare Access or VPN instead of weakening this file.

## Smoke Check

Run from a machine outside the server network:

```bash
export RPB_BASE_URL=https://acceptance.example.com
export RPB_BASIC_USER=reviewer
export RPB_BASIC_PASSWORD='replace-with-review-password'
bash deploy/public-acceptance/scripts/smoke-check.sh
```

To verify tenant logo media after uploading or seeding an acceptance-only logo:

```bash
export RPB_TENANT_LOGO_URL=/api/v1/stores/20000000-0000-0000-0000-000000000983/tenant-admin/profile/logo/media/<asset-id>
bash deploy/public-acceptance/scripts/smoke-check.sh
```

## Rollback

```bash
sudo systemctl stop rpb-backend
sudo rm -f /etc/nginx/sites-enabled/rpb-public-acceptance.conf
sudo systemctl reload nginx
```

To restore a previous artifact, copy the previous jar to `/opt/rpb/app/reservation-platform.jar`, restore the previous `/opt/rpb/frontend` directory, then restart:

```bash
sudo systemctl restart rpb-backend
sudo systemctl reload nginx
```

## Production Security Hardening Gate

Do not reuse this package as the final production deployment. Before production:

- Replace broad API permit rules with authenticated and App Gate protected rules.
- Make session cookies secure for HTTPS.
- Define production CORS and CSRF posture explicitly.
- Move media storage to the approved production storage design.
- Review migration order, rollback, logs, audit, tenant isolation, and permissions.
