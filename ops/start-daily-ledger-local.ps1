param([string]$ImportFile)
$ErrorActionPreference = 'Stop'
$ledgerRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ledgerSettings = @{}
Get-Content (Join-Path $ledgerRoot 'target/local-postgres-current.txt') | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { $ledgerSettings[$matches[1]] = $matches[2] }
}
if ((Resolve-Path $ledgerSettings.runtimeWorktree).Path -ne $ledgerRoot) { throw 'Pointer runtimeWorktree differs from this checkout.' }
if ($ledgerSettings.schema -notmatch '^daily_ledger_validation_[a-z0-9_]+$') { throw 'An isolated daily ledger validation schema must be configured in the pointer.' }
$ledgerOutput = Join-Path $ledgerRoot 'target/daily-ledger'
$ledgerSpring = @{
    spring = @{
        profiles = @{ active = 'local' }
        datasource = @{ url = "jdbc:postgresql://127.0.0.1:$($ledgerSettings.port)/postgres?stringtype=unspecified"; username = 'postgres'; password = ''; hikari = @{ schema = $ledgerSettings.schema } }
        flyway = @{ enabled = $false }
        jpa = @{ properties = @{ hibernate = @{ default_schema = $ledgerSettings.schema } } }
        http = @{ client = @{ factory = 'simple' } }
    }
    server = @{ port = [int]$ledgerSettings.backendPort }
    rpb = @{ 'local-auth' = @{ enabled = $false } }
}
if ($ImportFile) {
    if (-not $env:RPB_LEDGER_SEED_PASSWORD) { throw 'Set RPB_LEDGER_SEED_PASSWORD in this process before an explicit import.' }
    $ledgerSpring.rpb['daily-ledger'] = @{ 'import-file' = (Resolve-Path $ImportFile).Path; 'tenant-code' = 'sx'; 'seed-password' = $env:RPB_LEDGER_SEED_PASSWORD }
}
$env:SPRING_APPLICATION_JSON = $ledgerSpring | ConvertTo-Json -Depth 12 -Compress
try {
    $ledgerClasspath = (Join-Path $ledgerRoot 'target/classes') + ';' + [IO.File]::ReadAllText((Join-Path $ledgerOutput 'classpath.txt')).Trim()
    $ledgerProcess = Start-Process -FilePath (Get-Command java).Source -ArgumentList @("-Djdk.net.unixdomain.tmpdir=$ledgerOutput", '-cp', ('"' + $ledgerClasspath + '"'), 'com.rpb.reservation.ReservationPlatformApplication') -WorkingDirectory $ledgerRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $ledgerOutput 'backend.log') -RedirectStandardError (Join-Path $ledgerOutput 'backend-error.log')
    $ledgerProcess.Id | Set-Content (Join-Path $ledgerOutput 'backend.pid')
    Write-Output "Local backend PID=$($ledgerProcess.Id); schema=$($ledgerSettings.schema); port=$($ledgerSettings.backendPort)"
} finally { Remove-Item Env:SPRING_APPLICATION_JSON -ErrorAction SilentlyContinue }
