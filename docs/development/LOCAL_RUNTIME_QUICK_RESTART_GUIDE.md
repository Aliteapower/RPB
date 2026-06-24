# Local Runtime Quick Restart Guide

## Purpose

Use this guide when the full local development stack needs a fast restart against the existing local PostgreSQL runtime.

This captures the 2026-06-24 restart pitfalls so future development does not wait through repeated failed Spring Boot starts.

## Known Good Local Baseline

- Frontend: `http://127.0.0.1:5176`
- Backend: `http://127.0.0.1:8080`
- Store ID: `20000000-0000-0000-0000-000000000983`
- Tenant ID: `10000000-0000-0000-0000-000000000983`
- Actor ID: `30000000-0000-0000-0000-000000000983`
- PostgreSQL pointer file: `target/local-postgres-current.txt`
- Backend log: `target/local-backend.log`

The current local PostgreSQL pointer file should look like:

```text
port=61595
dataDir=D:\RPB\target\local-postgres-20260623203420
```

Use the port from the file. Do not hard-code `61595` unless the file still says that.

## Fast Checks Before Restart

```powershell
cd D:\RPB

Get-Content target/local-postgres-current.txt
Get-NetTCPConnection -LocalPort 5176,8080 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,State,OwningProcess
```

If frontend `5176` is already listening, do not restart it unless frontend assets are stale.

## Stop Only The Old Backend

```powershell
cd D:\RPB

Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ -Force }
```

Do not stop the PostgreSQL process from `target/local-postgres-current.txt`.

## Backend Restart Command

Run this from PowerShell. It starts Spring Boot in a hidden window and writes logs to `target/local-backend.log`.

```powershell
cd D:\RPB

$pgPort = (
  Get-Content target/local-postgres-current.txt |
  Where-Object { $_ -match '^port=' } |
  Select-Object -First 1
) -replace '^port=', ''
$log = 'D:\RPB\target\local-backend.log'

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
  'queue.call',
  'queue.seat',
  'queue.skip',
  'queue.rejoin',
  'walkin.direct_seating.create',
  'cleaning.start',
  'cleaning.complete',
  'table.view',
  'table.switch',
  'customer.lookup'
)

$permissionArgs = for ($i = 0; $i -lt $permissions.Count; $i++) {
  "--rpb.local-auth.permissions[$i]=$($permissions[$i])"
}

$bootArgs = @(
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$pgPort/postgres?stringtype=unspecified",
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
  'cd /d D:\RPB && ' +
  'set DB_HOST=127.0.0.1&& ' +
  'set DB_PORT=' + $pgPort + '&& ' +
  'set DB_NAME=postgres&& ' +
  'set DB_USERNAME=postgres&& ' +
  'set DB_PASSWORD=&& ' +
  'mvn spring-boot:run -Dspring-boot.run.profiles=local ' +
  '-Dspring-boot.run.arguments="' + $bootArgString + '" ' +
  '> target\local-backend.log 2>&1'

Start-Process -FilePath 'cmd.exe' -WindowStyle Hidden -ArgumentList @('/c', $cmd)
```

## Wait For Backend

```powershell
cd D:\RPB

$deadline = (Get-Date).AddSeconds(120)
do {
  Start-Sleep -Seconds 3
  $conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
} while (-not $conn -and (Get-Date) -lt $deadline)

$conn | Select-Object LocalAddress,LocalPort,State,OwningProcess
```

If no listener appears within 120 seconds, inspect the log:

```powershell
Get-Content D:\RPB\target\local-backend.log -Tail 200
```

## Runtime Smoke Checks

Use these before opening the browser.

```powershell
$storeId = '20000000-0000-0000-0000-000000000983'

Invoke-RestMethod "http://127.0.0.1:8080/api/me/apps?storeId=$storeId" |
  ConvertTo-Json -Depth 8

Invoke-RestMethod "http://127.0.0.1:8080/api/v1/stores/$storeId/tables" |
  ConvertTo-Json -Depth 6

Invoke-RestMethod "http://127.0.0.1:8080/api/v1/stores/$storeId/reservations/today?businessDate=2026-06-24" |
  ConvertTo-Json -Depth 8
```

Expected:

- `/api/me/apps` returns `reservation_queue`.
- `reservation_queue.permissions` contains `table.switch`.
- Table resources use `/api/v1/stores/{storeId}/tables`.
- Do not use the old `/table-resources` path; it is not the frontend path.

## Frontend Restart

Only restart frontend if `5176` is not listening or Vite has stale assets.

```powershell
cd D:\RPB
npm run dev -- --host 127.0.0.1 --port 5176
```

## Common Failures

| Symptom | Evidence | Cause | Fix |
| --- | --- | --- | --- |
| Maven fails immediately | `Unknown lifecycle phase ".run.profiles=local"` | PowerShell parsed `-Dspring-boot.run.profiles=local` incorrectly inside a command string | Use the `cmd.exe /c` wrapper above, or quote Maven `-D...` arguments carefully |
| Spring fails before JPA | `The server requested SCRAM-based authentication, but no password was provided` | App started against default `localhost:5432/reservation_platform` with `reservation_app` and blank password | Read `target/local-postgres-current.txt`; use `DB_NAME=postgres`, `DB_USERNAME=postgres`, blank password for this local trust database |
| Spring fails at Flyway | `Unsupported Database: PostgreSQL 17.10` | Current Flyway runtime does not support the local PostgreSQL version used by the validation database | Use `--spring.flyway.enabled=false` only after migrations have already been applied to the local validation DB |
| `/tables` or switch UI hidden | `/api/me/apps` lacks `table.view` or `table.switch` | Local auth permission list is incomplete | Include `table.view` and `table.switch` in `rpb.local-auth.permissions` |
| Direct table resource check returns 403 | Request was sent to `/table-resources` | That is not the current frontend/backend route | Use `/api/v1/stores/{storeId}/tables` |
| Change table button does not show | Today reservation item has no `seatingId` | No active seating resource exists, or the seating is already cleaning/released | This is expected; switch table only applies to active occupied seatings |

## Notes

- This local restart procedure does not add migrations or touch production data.
- `spring.flyway.enabled=false` is a local runtime workaround only.
- Keep the local auth permissions explicit. Do not broaden production security rules to make local validation easier.
- If local data looks stale, verify with `psql` before changing UI behavior.
