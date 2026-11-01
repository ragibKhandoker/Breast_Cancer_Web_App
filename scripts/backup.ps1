# Creates one encrypted backup of the MongoDB database (F20). Usage, from anywhere:
#   powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
# Needs MONGODB_URI and BACKUP_ENCRYPTION_KEY (in Backend\.env or the environment). Backups go to BACKUP_DIR
# (default: the backups folder next to Backend). Exit code 0 means the file was written and passed its integrity check.
# Windows Task Scheduler: run this script daily (see docs/F20_AUDIT_BLOCKCHAIN_BACKUP_PRIVACY.md).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..\Backend')
& .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.arguments=--backup.run=create --spring.main.web-application-type=none"
exit $LASTEXITCODE
