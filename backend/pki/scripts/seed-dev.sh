#!/usr/bin/env bash
set -euo pipefail

# Run from backend/pki
# Requires: DB env vars in .env or exported (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, MASTER_KEY)

./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=seed -Dspring-boot.run.arguments=--seed.exit=true
