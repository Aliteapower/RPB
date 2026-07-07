# Public Booking Deployment Runbook

## Purpose

Use this guide when deploying `booking.yumstone.sg` or diagnosing a public booking deployment failure.

This captures the 2026-07-04 deployment lessons so the next deployment does not depend on Codex chat history.

## Known Good Public Baseline

- Public site: `https://booking.yumstone.sg`
- Public login page: `https://booking.yumstone.sg/login`
- Platform admin login page: `https://platform.booking.yumstone.sg/login`
- Tenant login page example: `https://20000000.booking.yumstone.sg/login`
- Alphanumeric tenant login page example: `https://lsc106.booking.yumstone.sg/login`
- Tenant public booking entry example: `https://20000000.booking.yumstone.sg/book`
- Public backend health smoke target: `https://booking.yumstone.sg/api/v1/auth/me`
- Server: `43.134.69.75`
- SSH user: `ubuntu`
- SSH key on this workstation: `%USERPROFILE%\.ssh\codex_tt_lighthouse_ed25519`
- Backend service: `rpb-backend`
- Backend jar: `/opt/rpb/app/reservation-platform.jar`
- Frontend root: `/opt/rpb/frontend`
- Backend env file: `/etc/rpb/rpb-backend.env`
- Backend app port behind nginx: `127.0.0.1:8080`
- Nginx public listeners observed on 2026-07-05: ports `80` and `443`
- TLS certificate path: `/etc/letsencrypt/live/booking.yumstone.sg/fullchain.pem`
- Host-prefix login and tenant `/book` require wildcard DNS and TLS coverage for `*.booking.yumstone.sg`.
- Current production certificate is a Let's Encrypt SAN certificate, not a wildcard certificate. Add every live host prefix to the SAN list, or replace it with a DNS-validated wildcard certificate before claiming a new store code URL works.
- HTTP requests redirect to HTTPS.
- HTTPS responses include `Strict-Transport-Security: max-age=31536000`.

Do not commit private keys, production passwords, database passwords, or real customer credentials into this repository.

## Git Remote

The expected GitHub remote is:

```powershell
cd D:\RPB
git remote add origin https://github.com/Aliteapower/RPB.git
git push -u origin codex/staff-ui-v1-2-table-selection
```

If `origin` already exists, verify it instead of adding it again:

```powershell
cd D:\RPB
git remote -v
git status --short --branch
```

The 2026-07-04 deployment used branch `codex/staff-ui-v1-2-table-selection`.

## SSH Access Check

Use the deployment key explicitly. The server accepted `ubuntu`; direct SSH as `root` or `rpb` was not available.

```powershell
ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
  -o BatchMode=yes `
  -o IdentitiesOnly=yes `
  -o ConnectTimeout=8 `
  ubuntu@43.134.69.75 `
  "hostname && whoami && sudo -n true && systemctl is-active rpb-backend"
```

Expected output includes:

```text
VM-0-5-ubuntu
ubuntu
active
```

## Server Layout Check

```powershell
ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
  -o BatchMode=yes `
  -o IdentitiesOnly=yes `
  -o ConnectTimeout=8 `
  ubuntu@43.134.69.75 `
  "ls -ld /opt/rpb/app /opt/rpb/frontend /etc/rpb; systemctl cat rpb-backend"
```

Useful known paths:

- `/opt/rpb/app/reservation-platform.jar`
- `/opt/rpb/frontend`
- `/opt/rpb/backups`
- `/etc/rpb/rpb-backend.env`
- `/etc/systemd/system/rpb-backend.service`

The nginx config observed on 2026-07-04 served `/opt/rpb/frontend` and proxied `/api` to `http://127.0.0.1:8080`.

## Wildcard Host Prefix Requirements

The application treats the first DNS label as runtime context. The production domain must remain a deployment/runtime setting; do not hard-code `booking.yumstone.sg` in business code.

Required DNS and TLS shape:

```text
booking.yumstone.sg          A/AAAA -> public load balancer or VM
*.booking.yumstone.sg        A/AAAA -> same target
```

If production still uses the SAN certificate at `/etc/letsencrypt/live/booking.yumstone.sg/fullchain.pem`, expand it when a new store prefix must be public. Keep all existing domains in the command:

```powershell
$remoteScript = @'
set -euo pipefail
sudo -n certbot --nginx --cert-name booking.yumstone.sg \
  -d booking.yumstone.sg \
  -d platform.booking.yumstone.sg \
  -d 20000000.booking.yumstone.sg \
  -d lsc106.booking.yumstone.sg \
  -d lsc83.booking.yumstone.sg \
  --expand --non-interactive
sudo -n nginx -t
sudo -n systemctl reload nginx
sudo -n certbot certificates
'@

$remoteScript = $remoteScript -replace "`r", ""
$remoteScript |
  ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
    -o BatchMode=yes `
    -o IdentitiesOnly=yes `
    -o ConnectTimeout=8 `
    ubuntu@43.134.69.75 "bash -s"
```

Nginx should accept both the root host and wildcard hosts, then pass the original host to Spring Boot:

```nginx
server_name booking.yumstone.sg *.booking.yumstone.sg;

location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

### SAN Certificate Automation Without Wildcard

The application persists active host prefixes into `public_host_bindings` when `RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg` is configured. New tenant or store prefixes become rows with `tls_status = pending`.

Production can reconcile those pending rows without moving to a wildcard certificate by running:

```bash
sudo -n /opt/rpb/ops/reconcile-public-host-bindings.sh
```

The script:

- reads database settings from `/etc/rpb/rpb-backend.env`;
- bootstraps missing rows from active `tenant_host_aliases`;
- builds the SAN list from the existing certificate plus pending hostnames;
- runs `certbot --nginx --cert-name booking.yumstone.sg --expand --non-interactive`;
- runs `nginx -t` and `systemctl reload nginx`;
- marks rows `covered` on success or `failed` with `last_error` on failure.

Required environment:

```text
RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg
RPB_PUBLIC_HOST_CERT_NAME=booking.yumstone.sg
```

The script must run as a user with permission to run certbot and reload nginx. The business database stores only hostnames and TLS coverage status; certificate files and private keys stay in `/etc/letsencrypt` or the platform certificate store.

Expected host-prefix behavior:

- `platform.<deployment-domain>/login` exposes only the platform admin login entry.
- `<tenantCode>.<deployment-domain>/login` exposes tenant admin and staff login entries, hides the platform entry, and does not require typing or displaying the tenant code.
- DNS-safe alphanumeric prefixes such as `lsc106` are tenant prefixes. The root deployment host must remain the legacy compatibility entry.
- `<tenantCode>.<deployment-domain>/book` resolves the tenant's single enabled public booking store.
- Root domain and localhost keep the legacy login tabs and `/book/:storeId` compatibility path.

For deployment roots where label depth alone cannot distinguish the root host from a tenant host, set the backend runtime property through the service environment:

```text
RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg
```

The frontend resolver also supports the same deployment-root value via build-time or runtime configuration:

```text
VITE_RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg
window.__RPB_HOST_PREFIX_BASE_HOST__ = 'booking.yumstone.sg'
```

## Clean Build Rule

Always build deploy artifacts from a clean detached worktree at the commit being deployed. Do not build from `D:\RPB` when it has unrelated local changes.

```powershell
cd D:\RPB

$sha = (git rev-parse --short HEAD).Trim()
$deployWorktree = "target/deploy-worktree-$sha"

git worktree add --detach $deployWorktree HEAD
Push-Location $deployWorktree
mvn -DskipTests package
npm ci
npm run build
Pop-Location
```

For backend-only fixes, `npm ci` and `npm run build` can be skipped if the deployed frontend asset is already correct.

## Mandatory Jar Checks

Before uploading a backend jar, check that Flyway and all expected migrations are inside the jar.

```powershell
cd D:\RPB\target\deploy-worktree-<sha>

jar tf target\reservation-platform-0.0.1-SNAPSHOT.jar |
  Select-String -Pattern 'flyway-database-postgresql|V021__store_share_email|V022__tenant_onboarding|V023__tenant_subscription|V030__auth_account_scoped_username'
```

Expected entries for the 2026-07-04 fix:

```text
BOOT-INF/classes/db/migration/V021__store_share_email.sql
BOOT-INF/classes/db/migration/V022__tenant_onboarding_default_store_backfill.sql
BOOT-INF/classes/db/migration/V023__tenant_subscription_zero_amount_price_backfill.sql
BOOT-INF/classes/db/migration/V030__auth_account_scoped_username.sql
BOOT-INF/lib/flyway-database-postgresql-11.7.2.jar
```

Two important failure modes were found:

- Flyway 11 with PostgreSQL 16.14 needs `org.flywaydb:flyway-database-postgresql`; `flyway-core` alone failed with `Unsupported Database: PostgreSQL 16.14`.
- If production already applied a migration, the new jar must still contain that exact migration. A jar missing `V021__store_share_email.sql` failed with `Detected applied migration not resolved locally: 021`.

Do not use `flyway repair` for a missing migration unless the migration was intentionally removed and the database decision is documented. The normal fix is to add the missing migration back to Git and deploy a jar that contains it.

## Upload Backend Jar

```powershell
cd D:\RPB\target\deploy-worktree-<sha>

scp -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
  -o IdentitiesOnly=yes `
  -o ConnectTimeout=8 `
  target\reservation-platform-0.0.1-SNAPSHOT.jar `
  ubuntu@43.134.69.75:/home/ubuntu/rpb-<sha>.jar
```

## Deploy Backend Jar

Use LF-only remote scripts. PowerShell here-strings can carry CRLF into the remote shell; strip `"`r"` before piping to SSH.

```powershell
$sha = "<sha>"
$remoteScript = @'
set -euo pipefail

sha="__SHA__"
stamp="$(date +%Y%m%d-%H%M)-${sha}"
backup_dir="/opt/rpb/backups/${stamp}"

sudo -n mkdir -p "$backup_dir"
sudo -n cp -a /opt/rpb/app/reservation-platform.jar "$backup_dir/reservation-platform.jar"
sudo -n install -o rpb -g rpb -m 0644 "/home/ubuntu/rpb-${sha}.jar" /opt/rpb/app/reservation-platform.jar
sudo -n systemctl restart rpb-backend

state="unknown"
code="000"
for i in $(seq 1 75); do
  state="$(systemctl is-active rpb-backend || true)"
  code="$(curl -sS -o /tmp/rpb-auth-me.txt -w '%{http_code}' http://127.0.0.1:8080/api/v1/auth/me || true)"
  if [ "$state" = "active" ] && [ "$code" = "401" ]; then
    break
  fi
  if [ "$state" = "failed" ]; then
    break
  fi
  sleep 2
done

echo "backup_dir=$backup_dir"
echo "service_state=$state"
echo "auth_me_status=$code"
systemctl show rpb-backend -p ActiveState -p SubState -p ExecMainPID --no-pager

if [ "$state" != "active" ] || [ "$code" != "401" ]; then
  sudo -n journalctl -u rpb-backend -n 180 --no-pager
  exit 1
fi

sudo -n journalctl -u rpb-backend --since '6 minutes ago' --no-pager |
  grep -E 'Flyway|Migrating|Successfully applied|Unsupported Database|Started ReservationPlatformApplication' || true
'@

$remoteScript = $remoteScript.Replace('__SHA__', $sha) -replace "`r", ""
$remoteScript |
  ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
    -o BatchMode=yes `
    -o IdentitiesOnly=yes `
    -o ConnectTimeout=8 `
    ubuntu@43.134.69.75 "bash -s"
```

For the 2026-07-04 deployment, successful logs included:

```text
Migrating schema "public" to version "022 - tenant onboarding default store backfill"
Migrating schema "public" to version "023 - tenant subscription zero amount price backfill"
Successfully applied 2 migrations to schema "public", now at version v023
Started ReservationPlatformApplication
```

## Frontend Deployment Notes

Frontend artifacts are served from `/opt/rpb/frontend`. The 2026-07-04 frontend asset was:

```text
/assets/index-CQ01OImY.js
```

Verify the public page loads the expected asset:

```powershell
Invoke-WebRequest -Uri 'https://booking.yumstone.sg/login' -UseBasicParsing -TimeoutSec 15 |
  Select-Object StatusCode,@{Name='Asset';Expression={ if ($_.Content -match '/assets/index-[^"'']+\.js') { $Matches[0] } else { 'missing' } }}
```

If deploying frontend, build from the clean worktree and upload a tarball:

```powershell
cd D:\RPB\target\deploy-worktree-<sha>
npm ci
npm run build
tar -czf ..\rpb-<sha>-frontend.tgz -C dist .

scp -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
  -o IdentitiesOnly=yes `
  -o ConnectTimeout=8 `
  ..\rpb-<sha>-frontend.tgz `
  ubuntu@43.134.69.75:/home/ubuntu/rpb-<sha>-frontend.tgz
```

When unpacking remotely, avoid root-owned temporary files that the `rpb` user cannot read. Create a temporary directory and set ownership before extracting if needed.

## Database History Check

Read database connection values from `/etc/rpb/rpb-backend.env` through `sudo`. Do not print the password.

```powershell
$remoteScript = @'
set -euo pipefail
sudo -n bash -c '
  set -a
  . /etc/rpb/rpb-backend.env
  set +a
  PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -tAc "select version, description, success from flyway_schema_history order by installed_rank desc limit 5"
'
'@

$remoteScript = $remoteScript -replace "`r", ""
$remoteScript |
  ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
    -o BatchMode=yes `
    -o IdentitiesOnly=yes `
    -o ConnectTimeout=8 `
    ubuntu@43.134.69.75 "bash -s"
```

Expected after the 2026-07-04 deployment:

```text
023|tenant subscription zero amount price backfill|t
022|tenant onboarding default store backfill|t
021|store share email|t
020|store whatsapp share profile|t
019|reservation meal period schedule|t
```

## Smoke Tests

Backend unauthenticated smoke test should return `401`, not `502` or `000`:

```powershell
curl.exe -sS -o NUL -w "%{http_code}" https://booking.yumstone.sg/api/v1/auth/me
```

Server-side equivalent:

```powershell
ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
  -o BatchMode=yes `
  -o IdentitiesOnly=yes `
  -o ConnectTimeout=8 `
  ubuntu@43.134.69.75 `
  "systemctl is-active rpb-backend; curl -sS -o /tmp/rpb-auth-me.txt -w '%{http_code}' http://127.0.0.1:8080/api/v1/auth/me"
```

Expected:

```text
active
401
```

Host-prefix smoke targets after deploying the login entry change:

```powershell
curl.exe -sS -o NUL -w "%{http_code}" https://platform.booking.yumstone.sg/login
curl.exe -sS -o NUL -w "%{http_code}" https://20000000.booking.yumstone.sg/login
curl.exe -sS -o NUL -w "%{http_code}" https://20000000.booking.yumstone.sg/book
```

Expected frontend status is `200`. The tenant `/book` page will call `/api/v1/public/booking-entry`; if the tenant has multiple enabled public booking stores, it should fail explicitly instead of guessing a store.

For tenant login validation, use a user-provided test account without writing the password to docs or logs. The key assertion for a tenant admin is that the login response contains a non-null `defaultStoreId` and a non-empty `storeIds` array.

The 2026-07-04 failure before the fix returned:

```json
{
  "actorType": "tenant_admin",
  "defaultStoreId": null,
  "storeIds": []
}
```

The successful shape after V022 was:

```json
{
  "actorType": "tenant_admin",
  "defaultStoreId": "<store-id>",
  "storeIds": ["<store-id>"],
  "roles": ["tenant_admin"],
  "permissions": ["tenant.admin.manage"]
}
```

## Rollback

Every backend deploy script must print the backup directory. To roll back a failed backend jar:

```powershell
$backupDir = "/opt/rpb/backups/<printed-backup-dir>"
$remoteScript = @'
set -euo pipefail
backup_dir="__BACKUP_DIR__"
sudo -n install -o rpb -g rpb -m 0644 "$backup_dir/reservation-platform.jar" /opt/rpb/app/reservation-platform.jar
sudo -n systemctl restart rpb-backend
systemctl is-active rpb-backend
curl -sS -o /tmp/rpb-auth-me.txt -w '%{http_code}' http://127.0.0.1:8080/api/v1/auth/me
'@

$remoteScript = $remoteScript.Replace('__BACKUP_DIR__', $backupDir) -replace "`r", ""
$remoteScript |
  ssh -i "$env:USERPROFILE\.ssh\codex_tt_lighthouse_ed25519" `
    -o BatchMode=yes `
    -o IdentitiesOnly=yes `
    -o ConnectTimeout=8 `
    ubuntu@43.134.69.75 "bash -s"
```

Expected rollback smoke output:

```text
active
401
```

## 2026-07-04 Incident Notes

Symptoms:

- Public login page stayed on `登录中...`.
- New tenant login initially returned no store scope.
- First backend deploy failed with `Unsupported Database: PostgreSQL 16.14`.
- Second backend deploy failed with `Detected applied migration not resolved locally: 021`.

Root causes:

- The login API did not have a frontend timeout path, so a stuck request kept the button loading.
- A newly created tenant admin account had no default store/store access until V022 backfilled the onboarding state.
- The production jar missed `flyway-database-postgresql`, which Flyway 11 needs for PostgreSQL 16.14.
- The deployment commit initially missed `V021__store_share_email.sql`, while production had already applied V021.

Fixes deployed:

- `56c6c93 fix: prevent public login from hanging`
- `58574ee fix: bootstrap tenant login and billing prices`
- `d729ea3 fix: include flyway postgresql runtime support`
- `c46aab3 fix: include store share email migration`

Final verification:

- `rpb-backend` active.
- Public `/api/v1/auth/me` returned `401`.
- Flyway history reached `v023`.
- Tenant admin login returned a non-null `defaultStoreId` and non-empty `storeIds`.

## Pre-Deployment Checklist

- Confirm `git status --short --branch`; avoid packaging unrelated dirty files.
- Confirm `origin` is `https://github.com/Aliteapower/RPB.git`.
- Confirm SSH as `ubuntu` works with the deployment key.
- Build from a clean detached worktree at the exact commit to deploy.
- Check the jar contains `flyway-database-postgresql`.
- Check the jar contains every migration already applied in production.
- Upload artifacts with commit SHA in the filename.
- Back up `/opt/rpb/app/reservation-platform.jar` before replacing it.
- Restart `rpb-backend` and wait for `/api/v1/auth/me` to return `401`.
- Check Flyway logs and `flyway_schema_history`.
- Check public frontend asset if frontend changed.
- Validate tenant login response includes the expected store scope.
