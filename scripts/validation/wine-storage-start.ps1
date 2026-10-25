$ErrorActionPreference = 'Stop'
# Explicit isolated local runtime; do not alter the pointer or stop existing services.
$storageRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$storagePointer = @{}
Get-Content (Join-Path $storageRoot 'target/local-postgres-current.txt') | Where-Object { $_ -match '=' } | ForEach-Object {
    $storageKey, $storageValue = $_ -split '=', 2
    $storagePointer[$storageKey] = $storageValue
}
$storageFixture = Get-Content (Join-Path $storageRoot 'target/wine-storage-validation/fixture.json') -Raw | ConvertFrom-Json
if ($storagePointer.runtimeWorktree -ne $storageRoot -or $storageFixture.pgPort -ne $storagePointer.port -or $storageFixture.schema -notmatch '^wallet_browser_[a-f0-9]{32}$') { throw 'Wrong local runtime ownership or pointer' }
if ($storageFixture.backendPort -ne 8086) { throw 'Unexpected isolated backend port' }
if (netstat -ano | Select-String ':8086\s+.*LISTENING') { throw 'Port 8086 is already owned by another process; inspect before reuse' }
$storageJar = Join-Path $storageRoot 'target/reservation-platform-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $storageJar)) { throw 'Build the current backend first' }
$storageRuntimeJar = Join-Path $storageRoot 'target/wine-storage-validation/backend-runtime.jar'
Copy-Item -LiteralPath $storageJar -Destination $storageRuntimeJar -Force
$storageUds = Join-Path $storageRoot 'target/wine-storage-uds'
New-Item -ItemType Directory -Force -Path $storageUds | Out-Null
$storageArgs = @(
    "-Djdk.net.unixdomain.tmpdir=$storageUds",
    '-jar', $storageRuntimeJar, '--server.address=127.0.0.1', '--server.port=8086',
    "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($storagePointer.port)/postgres?stringtype=unspecified",
    '--spring.datasource.username=postgres', '--spring.datasource.password=',
    "--spring.datasource.hikari.schema=$($storageFixture.schema)",
    "--spring.jpa.properties.hibernate.default_schema=$($storageFixture.schema)",
    '--spring.flyway.enabled=false', '--rpb.local-auth.enabled=false', '--spring.http.client.factory=simple'
)
$storageProcess = Start-Process -FilePath (Get-Command java).Source -WorkingDirectory $storageRoot -ArgumentList $storageArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $storageRoot 'target/wine-storage-validation/backend.log') -RedirectStandardError (Join-Path $storageRoot 'target/wine-storage-validation/backend-error.log')
$storageProcess.Id | Set-Content (Join-Path $storageRoot 'target/wine-storage-validation/backend.pid')
Write-Output "Isolated local backend pid=$($storageProcess.Id), port=8086, schema=$($storageFixture.schema)"
