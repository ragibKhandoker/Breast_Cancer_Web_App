# Restores one backup into a SEPARATE database (<database>_restore_test), checks the records, then drops the copy.
# The live database is never touched. Run this on a test server, not production. Usage:
#   powershell -ExecutionPolicy Bypass -File Backend\scripts\restore-test.ps1 backup_2026-11-01_02-00-00.bcbk
param([Parameter(Mandatory = $true)][string]$File)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=--backup.run=restore-test --backup.file=$File --backup.restore.enabled=true --spring.main.web-application-type=none"
exit $LASTEXITCODE
