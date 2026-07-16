# Local Runtime Quick Restart Guide

## Purpose

Use this guide when the full local development stack needs a fast restart against the existing local PostgreSQL runtime.

This captures the 2026-06-24, 2026-06-25, and 2026-07-01 restart pitfalls so future development does not wait through repeated failed Spring Boot starts.

## Known Good Local Baseline

- Default frontend: `http://127.0.0.1:5176`
- Default backend: `http://127.0.0.1:8080`
- Store ID: `20000000-0000-0000-0000-000000000983`
- Tenant ID: `10000000-0000-0000-0000-000000000983`
- Actor ID: `30000000-0000-0000-0000-000000000983`
- PostgreSQL pointer file: `target/local-postgres-current.txt`
- Default backend log: `target/local-backend.log`
- Login page: `http://127.0.0.1:5176/login`
- Validation tables page: `http://127.0.0.1:5176/stores/20000000-0000-0000-0000-000000000983/tables`
- Auth test password: `393930`

If `target/local-postgres-current.txt` includes `runtimeWorktree`, `backendPort`, `frontendPort`, `alternateFrontendPort`, or `mediaStorageRoot`, those values override this baseline. Public booking validation commonly uses backend `8081`, frontend `5177`, and an isolated `runtimeWorktree`.

## Mandatory Database Rule

Every local backend restart, browser smoke test, manual migration check, and ad hoc SQL query must use the PostgreSQL runtime pointed to by `target/local-postgres-current.txt`.

- Read the pointer file immediately before validation and use its `port`; do not rely on a remembered port.
- Use `jdbc:postgresql://127.0.0.1:<pointer-port>/postgres?stringtype=unspecified`, database `postgres`, username `postgres`, and a blank password.
- Do not use `localhost:5432`, `reservation_platform`, the system PostgreSQL service, or another worktree's backend/database as a substitute.
- In isolated worktrees, make the backend under test use the same pointer database as the frontend/backend smoke target. Do not validate a page against a Vite proxy connected to a different branch or database.
- Apply new local-only migration checks to the pointer database before restarting a backend with `--spring.flyway.enabled=false`.
- If the pointer includes `runtimeWorktree`, start backend and frontend from that directory, not from `D:\RPB`.
- Use `mediaStorageRoot` from the pointer when it exists. Isolated worktrees can share the pointer database while their relative `target/call-screen-media` directory is empty, which breaks tenant logo and call-screen media previews.

Local auth validation accounts:

| Scope | Username | Password | Actor type |
| --- | --- | --- | --- |
| Platform admin | `sysadmin` | `393930` | `platform_admin` |
| Tenant admin | `20000000` | `393930` | `tenant_admin` |
| Tenant staff | `1000` | `393930` | `staff` |

Passwords are six characters and may use digits or English letters. Letter case is ignored before password verification.

The current local PostgreSQL pointer file should look like:

```text
port=61595
dataDir=D:\RPB\target\local-postgres-20260623203420
runtimeWorktree=C:\Users\ZhangXianLi\.config\superpowers\worktrees\RPB\public-booking
backendPort=8081
frontendPort=5176
alternateFrontendPort=5177
apiProxyTarget=http://127.0.0.1:8081
mediaStorageRoot=D:\RPB\target\call-screen-media
```

Use the port and optional runtime fields from the file. Do not hard-code `61595`, `8080`, or `5176` unless the file still says that.

## Fast Checks Before Restart

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$pgPort = [int] $settings.port
$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$frontendPort = if ($settings.ContainsKey('alternateFrontendPort')) {
  [int] $settings.alternateFrontendPort
} elseif ($settings.ContainsKey('frontendPort')) {
  [int] $settings.frontendPort
} else {
  5176
}

Get-Content target/local-postgres-current.txt
netstat -ano | Select-String ":($pgPort|$backendPort|$frontendPort)\s"
```

Avoid `Get-NetTCPConnection` for quick checks on this machine; it has hung during local restarts. Use `netstat`, `pg_isready`, and HTTP checks with short timeouts instead.

If the pointer frontend port is already listening, do not restart it unless frontend assets are stale.

## Start Existing Local PostgreSQL If Needed

The pointer file may exist even when the local validation PostgreSQL process has stopped. In that case Spring Boot reaches JPA and then fails with `Connection to 127.0.0.1:<port> refused`.

First check the pointer port, not the system PostgreSQL service on `5432`:

```powershell
cd D:\RPB

$pointer = Get-Content target/local-postgres-current.txt
$pgPort = (
  $pointer |
  Where-Object { $_ -match '^port=' } |
  Select-Object -First 1
) -replace '^port=', ''
$pgDataDir = (
  $pointer |
  Where-Object { $_ -match '^dataDir=' } |
  Select-Object -First 1
) -replace '^dataDir=', ''

netstat -ano | Select-String ":$pgPort\s+.*LISTENING"
```

If the command returns no listener, start the existing data directory. Do not create a new cluster for a quick restart.

```powershell
cd D:\RPB

$pointer = Get-Content target/local-postgres-current.txt
$pgPort = (
  $pointer |
  Where-Object { $_ -match '^port=' } |
  Select-Object -First 1
) -replace '^port=', ''
$pgDataDir = (
  $pointer |
  Where-Object { $_ -match '^dataDir=' } |
  Select-Object -First 1
) -replace '^dataDir=', ''

$pgCtl = (Get-Command pg_ctl.exe -ErrorAction SilentlyContinue).Source
if (-not $pgCtl) {
  $pgCtl = 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe'
}

$pgLog = "target\local-postgres-$pgPort.log"
& $pgCtl -D $pgDataDir -l $pgLog -o "`"-p`" `"$pgPort`" `"-h`" `"127.0.0.1`"" -w start
```

Expected successful output includes `listening on IPv4 address "127.0.0.1", port <port>` and `database system is ready to accept connections`.

Always pass `-l` when starting PostgreSQL from automation. Without a log file, PostgreSQL can keep writing to the current tool session and make a quick restart look stuck even after the server is running.

If another `postgres.exe` is already running on `5432`, leave it alone. It is the system PostgreSQL service and is not the same runtime as `target/local-postgres-current.txt`.

## Apply V003 Auth Migration If Needed

The quick backend command below uses `--spring.flyway.enabled=false` because the current local validation database runs PostgreSQL 17. Apply new migrations to the pointer database before restarting the backend.

Check whether V003 has already been applied:

```powershell
cd D:\RPB

$pgPort = (
  Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '^port=' } |
  Select-Object -First 1
) -replace '^port=', ''
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
  $psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
}

& $psql -h 127.0.0.1 -p $pgPort -U postgres -d postgres -Atc "select to_regclass('public.auth_accounts');"
```

If the command returns `auth_accounts`, do not run the full V003 file again. It contains `create table` statements and is not meant to be replayed by hand.

If the command returns an empty value, apply V003 once:

```powershell
cd D:\RPB

$pgPort = (
  Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '^port=' } |
  Select-Object -First 1
) -replace '^port=', ''
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
  $psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
}

& $psql -h 127.0.0.1 -p $pgPort -U postgres -d postgres -v ON_ERROR_STOP=1 -f src\main\resources\db\migration\V003__auth_minimal_login.sql
```

Verify the seeded accounts:

```powershell
& $psql -h 127.0.0.1 -p $pgPort -U postgres -d postgres -Atc "select username || ':' || actor_type || ':' || status from auth_accounts order by username;"
```

Expected:

```text
1000:staff:active
20000000:tenant_admin:active
sysadmin:platform_admin:active
```

## Stop Only The Old Backend And Frontend

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$frontendPort = if ($settings.ContainsKey('alternateFrontendPort')) {
  [int] $settings.alternateFrontendPort
} elseif ($settings.ContainsKey('frontendPort')) {
  [int] $settings.frontendPort
} else {
  5176
}

$portsToStop = @($backendPort, $frontendPort)
netstat -ano | ForEach-Object {
  if ($_ -match '^\s*TCP\s+\S+:(\d+)\s+\S+\s+LISTENING\s+(\d+)\s*$') {
    $port = [int] $matches[1]
    $processId = [int] $matches[2]
    if ($portsToStop -contains $port) {
      Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
  }
}
```

Do not stop the PostgreSQL process from `target/local-postgres-current.txt`.

## Backend Restart Command

Run this from PowerShell. It starts Spring Boot in a hidden window from the pointer worktree and writes logs to `target/local-backend-<port>.log`.

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$pgPort = [int] $settings.port
$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$worktree = if ($settings.ContainsKey('runtimeWorktree')) {
  $settings.runtimeWorktree
} else {
  (Get-Location).Path
}
$mediaStorageRoot = if ($settings.ContainsKey('mediaStorageRoot')) {
  $settings.mediaStorageRoot
} else {
  Join-Path (Get-Location).Path 'target\call-screen-media'
}

$permissions = @(
  'reservation.create',
  'reservation.today_view',
  'reservation.check_in',
  'reservation.cancel',
  'reservation.no_show',
  'reservation.complete',
  'reservation.seat',
  'reservation.queue',
  'queue.view',
  'queue.display.view',
  'queue.call',
  'queue.seat',
  'queue.skip',
  'queue.rejoin',
  'queue.cancel',
  'walkin.direct_seating.create',
  'walkin.queue.create',
  'cleaning.start',
  'cleaning.complete',
  'table.view',
  'table.switch',
  'customer.lookup',
  'platform.call_screen_ad.manage'
)

$permissionArgs = for ($i = 0; $i -lt $permissions.Count; $i++) {
  "--rpb.local-auth.permissions[$i]=$($permissions[$i])"
}

$bootArgs = @(
  "--server.port=$backendPort",
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$pgPort/postgres?stringtype=unspecified",
  "--rpb.call-screen-media.storage-root=$mediaStorageRoot",
  '--spring.datasource.username=postgres',
  '--spring.datasource.password=',
  '--spring.flyway.enabled=false',
  '--rpb.local-auth.enabled=true',
  '--rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983',
  '--rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000983',
  '--rpb.local-auth.actor-type=staff',
  '--rpb.local-auth.roles[0]=store_staff',
  '--rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000983'
) + $permissionArgs

$bootArgString = ($bootArgs -join ' ')
$cmd =
  'set DB_HOST=127.0.0.1&& ' +
  'set DB_PORT=' + $pgPort + '&& ' +
  'set DB_NAME=postgres&& ' +
  'set DB_USERNAME=postgres&& ' +
  'set DB_PASSWORD=&& ' +
  'mvn spring-boot:run "-Dspring-boot.run.profiles=local" ' +
  '"-Dspring-boot.run.arguments=' + $bootArgString + '" ' +
  '> target\local-backend-' + $backendPort + '.log 2>&1'

Start-Process -FilePath 'cmd.exe' -WorkingDirectory $worktree -WindowStyle Hidden -ArgumentList @('/c', $cmd)
```

Use `Start-Process -WorkingDirectory $worktree` instead of building `cd /d \"...\"` into the command string. Escaped quotes inside `cmd.exe /c` can leave Maven running from the wrong branch and waste minutes on a backend that is not the one being validated.

For tenant-admin or public-booking validation, change the local auth section in `$bootArgs` to tenant admin:

```powershell
'--rpb.local-auth.actor-type=tenant_admin',
'--rpb.local-auth.roles[0]=tenant_admin',
'--rpb.local-auth.permissions[0]=tenant.admin.manage'
```

## Wait For Backend

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$deadline = (Get-Date).AddSeconds(120)
do {
  Start-Sleep -Seconds 3
  $code = curl.exe --silent --show-error --max-time 5 --output NUL --write-out '%{http_code}' "http://127.0.0.1:$backendPort/api/v1/auth/me" 2>$null
  $ready = $code -match '^(200|401|403)$'
} while (-not $ready -and (Get-Date) -lt $deadline)

netstat -ano | Select-String ":$backendPort\s+.*LISTENING"
```

If no listener appears within 120 seconds, inspect the log:

```powershell
$worktree = if ($settings.ContainsKey('runtimeWorktree')) { $settings.runtimeWorktree } else { 'D:\RPB' }
Get-Content (Join-Path $worktree "target\local-backend-$backendPort.log") -Tail 200
```

## Runtime Smoke Checks

Use these before opening the browser.

```powershell
$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$api = "http://127.0.0.1:$backendPort"
$storeId = '20000000-0000-0000-0000-000000000983'

Invoke-RestMethod "$api/api/me/apps?storeId=$storeId" |
  ConvertTo-Json -Depth 8

Invoke-RestMethod "$api/api/v1/stores/$storeId/tables" |
  ConvertTo-Json -Depth 6

Invoke-RestMethod "$api/api/v1/stores/$storeId/reservations/today?businessDate=2026-06-24" |
  ConvertTo-Json -Depth 8

Invoke-RestMethod "$api/api/v1/stores/$storeId/staff-home/overview?businessDate=2026-06-25" |
  ConvertTo-Json -Depth 8
```

Expected:

- `/api/me/apps` returns `reservation_queue`.
- `reservation_queue.permissions` contains `table.switch`.
- `reservation_queue.permissions` contains `walkin.queue.create`.
- `reservation_queue.permissions` contains `queue.cancel`.
- `reservation_queue.permissions` contains `queue.display.view`.
- Platform text seed maintenance requires `platform_admin` plus `platform.call_screen_ad.manage`; existing platform sessions with `platform.tenant.manage` are also accepted for compatibility. The local validation migration grants the dedicated permission to the `sysadmin` account; do not grant it to tenant admin or store staff accounts.
- Table resources use `/api/v1/stores/{storeId}/tables`.
- Staff home today overview uses `/api/v1/stores/{storeId}/staff-home/overview`.
- Do not use the old `/table-resources` path; it is not the frontend path.

## Auth Smoke Checks

The login page uses `/api/v1/auth/captcha/slider`, `/api/v1/auth/login`, `/api/v1/auth/me`, and `/api/v1/auth/logout`.

Basic auth endpoint checks:

```powershell
$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$api = "http://127.0.0.1:$backendPort"

Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/captcha/slider" |
  Select-Object success

try {
  Invoke-WebRequest -Uri "$api/api/v1/auth/me" -UseBasicParsing
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expected:

- Captcha creation returns `success: True`.
- `/api/v1/auth/me` returns `401` without a session cookie.

For a local-only full login smoke check, generate a challenge and read the expected slider X from the local validation database. This avoids manual dragging while still exercising login, session, `/me`, and logout.

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$pgPort = [int] $settings.port
$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$api = "http://127.0.0.1:$backendPort"
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
  $psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$captcha = Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/captcha/slider" -WebSession $session
$challengeId = $captcha.challenge.challengeId
$targetX = [int] (& $psql -h 127.0.0.1 -p $pgPort -U postgres -d postgres -Atc "select target_x from auth_slider_captcha_challenges where id = '$challengeId'::uuid;")

$body = @{
  username = 'sysadmin'
  password = '393930'
  captchaId = $challengeId
  captchaX = $targetX
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/login" -ContentType 'application/json' -Body $body -WebSession $session
$me = Invoke-RestMethod -Method Get -Uri "$api/api/v1/auth/me" -WebSession $session
$logout = Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/logout" -WebSession $session

[pscustomobject]@{
  loginSuccess = $login.success
  username = $me.user.username
  actorType = $me.user.actorType
  logoutSuccess = $logout.success
} | ConvertTo-Json -Depth 6
```

Expected `actorType` for this smoke check is `platform_admin`.

## Frontend Restart

Only restart frontend if the pointer frontend port is not listening or Vite has stale assets.

```powershell
cd D:\RPB

$settings = @{}
Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '=' } |
  ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key] = $value
  }

$backendPort = if ($settings.ContainsKey('backendPort')) { [int] $settings.backendPort } else { 8080 }
$frontendPort = if ($settings.ContainsKey('alternateFrontendPort')) {
  [int] $settings.alternateFrontendPort
} elseif ($settings.ContainsKey('frontendPort')) {
  [int] $settings.frontendPort
} else {
  5176
}
$worktree = if ($settings.ContainsKey('runtimeWorktree')) {
  $settings.runtimeWorktree
} else {
  (Get-Location).Path
}

$cmd = 'set VITE_API_PROXY_TARGET=http://127.0.0.1:' + $backendPort +
  '&& npm run dev -- --host 127.0.0.1 --port ' + $frontendPort +
  ' > target\local-frontend-' + $frontendPort + '.log 2>&1'

Start-Process -FilePath 'cmd.exe' -WorkingDirectory $worktree -WindowStyle Hidden -ArgumentList @('/c', $cmd)
```

## Common Failures

| Symptom | Evidence | Cause | Fix |
| --- | --- | --- | --- |
| Maven fails immediately | `Unknown lifecycle phase ".run.profiles=local"` | PowerShell parsed `-Dspring-boot.run.profiles=local` incorrectly inside a command string | Use the `cmd.exe /c` wrapper above, or quote Maven `-D...` arguments carefully |
| Port check hangs for minutes | `Get-NetTCPConnection` never returns | Windows TCP cmdlet can hang in this local environment | Use `netstat -ano`, `pg_isready`, and `curl.exe --max-time` checks from this guide |
| PostgreSQL start appears stuck after it is ready | Repeated PostgreSQL logs keep printing in the tool session | `pg_ctl start` was run without `-l`, so the server inherited the current session output | Stop the pointer PostgreSQL if needed, then start it with `pg_ctl -l target\local-postgres-<port>.log ...` |
| Backend starts but is the wrong source version | Logs show `started by ... in D:\RPB` when pointer says `runtimeWorktree=...` | Backend was launched from the main workspace instead of the pointer worktree | Read `runtimeWorktree` and use `Start-Process -WorkingDirectory $worktree` |
| Backend command silently fails to switch directory | Command contains `cd /d \"C:\...\"` or similar escaped quotes | `cmd.exe /c` does not treat PowerShell-style escaped quotes as intended | Do not put `cd /d` in the command; use `Start-Process -WorkingDirectory` |
| Spring fails at JPA startup | `Connection to 127.0.0.1:<pointer-port> refused` | `target/local-postgres-current.txt` points to a local PostgreSQL data directory, but that process is not listening | Run `Start Existing Local PostgreSQL If Needed`, then restart only the backend |
| Spring dies after PostgreSQL restart | Hikari warns that existing connections are closed, then Maven reports `Process terminated with exit code: -1` | PostgreSQL was restarted underneath a running backend | Restart backend after PostgreSQL is up; do not wait for the old backend to recover |
| Spring fails before JPA | `The server requested SCRAM-based authentication, but no password was provided` | App started against default `localhost:5432/reservation_platform` with `reservation_app` and blank password | Read `target/local-postgres-current.txt`; use `DB_NAME=postgres`, `DB_USERNAME=postgres`, blank password for this local trust database |
| Spring fails at Flyway | `Unsupported Database: PostgreSQL 17.10` | Current Flyway runtime does not support the local PostgreSQL version used by the validation database | Use `--spring.flyway.enabled=false` only after migrations have already been applied to the local validation DB |
| V003 fails when run manually | `relation "auth_accounts" already exists` | V003 was already applied to the local validation DB | Do not replay the full V003 file; verify seeded rows instead |
| Spring fails before Tomcat starts | `A filter chain that matches any request ... has already been configured` | Both default auth security and local runtime security registered an `any request` filter chain | Ensure the current `AuthSecurityConfiguration` is compiled; with `rpb.local-auth.enabled=true`, only `LocalRuntimeSecurityConfiguration` should own the local chain |
| Login returns `auth.captcha_mismatch` | Login request sent a scaled slider coordinate instead of the original image coordinate, or the challenge expired | Refresh the captcha and send `captchaX` in the original 320px image coordinate space; the Vue login page handles this scaling |
| `/tables` or switch UI hidden | `/api/me/apps` lacks `table.view` or `table.switch` | Local auth permission list is incomplete | Include `table.view` and `table.switch` in `rpb.local-auth.permissions` |
| Walk-in quick ticket returns `appgate.permission_denied` | `/api/me/apps` lacks `walkin.queue.create` | Local auth permission list is incomplete | Include `walkin.queue.create` in `rpb.local-auth.permissions` |
| Queue cancel button returns `appgate.permission_denied` | `/api/me/apps` lacks `queue.cancel` | Local auth permission list is incomplete | Include `queue.cancel` in `rpb.local-auth.permissions` |
| Direct table resource check returns 403 | Request was sent to `/table-resources` | That is not the current frontend/backend route | Use `/api/v1/stores/{storeId}/tables` |
| Change table button does not show | Today reservation item has no `seatingId` | No active seating resource exists, or the seating is already cleaning/released | This is expected; switch table only applies to active occupied seatings |
| Tenant logo image returns `404` | Tenant profile API returns `logoMediaUrl`, but the image request fails and the pointer has `runtimeWorktree=...` | The pointer database references media files under another worktree's `target\call-screen-media`, while Spring Boot started from the isolated worktree's empty relative media directory | Use `mediaStorageRoot` from the pointer and pass `--rpb.call-screen-media.storage-root=<shared-media-root>` when starting backend |

## Notes

- This local restart procedure does not add migrations or touch production data.
- `spring.flyway.enabled=false` is a local runtime workaround only; apply required local migrations manually before restart.
- Keep the local auth permissions explicit. Do not broaden production security rules to make local validation easier.
- If local data looks stale, verify with `psql` before changing UI behavior.
