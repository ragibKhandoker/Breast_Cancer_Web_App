#!/usr/bin/env sh
# Creates one encrypted backup of the MongoDB database (F20). Usage: sh Backend/scripts/backup.sh
# Needs MONGODB_URI and BACKUP_ENCRYPTION_KEY (in Backend/.env or the environment). Exit code 0 means the file was
# written and passed its integrity check. cron example (02:00 daily): 0 2 * * * cd /path/to/Cancer_web && sh Backend/scripts/backup.sh
set -e
cd "$(dirname "$0")/.."
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--backup.run=create --spring.main.web-application-type=none"
