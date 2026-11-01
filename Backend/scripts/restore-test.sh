#!/usr/bin/env sh
# Restores one backup into a SEPARATE database (<database>_restore_test), checks the records, then drops the copy.
# The live database is never touched. Run this on a test server. Usage: sh Backend/scripts/restore-test.sh backup_YYYY-MM-DD_HH-MM-SS.bcbk
set -e
[ -n "$1" ] || { echo "usage: $0 backup_YYYY-MM-DD_HH-MM-SS.bcbk"; exit 2; }
cd "$(dirname "$0")/.."
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--backup.run=restore-test --backup.file=$1 --backup.restore.enabled=true --spring.main.web-application-type=none"
