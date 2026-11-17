#!/usr/bin/env bash
# Sauvegarde de la base PostgreSQL et de la GED (docker compose), avec rotation.
# Usage : outils/sauvegarde.sh [répertoire] [jours de rétention]   — à planifier en cron (ex. : 0 2 * * *)
set -euo pipefail
DEST=${1:-/var/backups/gcm}
JOURS=${2:-30}
HORO=$(date +%Y%m%d-%H%M%S)
COMPOSE=${COMPOSE:-docker compose -f docker-compose.yml -f docker-compose.prod.yml}
mkdir -p "$DEST"
$COMPOSE exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$DEST/gcm-base-$HORO.dump"
$COMPOSE exec -T api tar -C /data -czf - ged > "$DEST/gcm-ged-$HORO.tar.gz"
find "$DEST" -name 'gcm-*' -mtime +"$JOURS" -delete
echo "Sauvegarde terminée : $DEST/gcm-base-$HORO.dump, $DEST/gcm-ged-$HORO.tar.gz"
