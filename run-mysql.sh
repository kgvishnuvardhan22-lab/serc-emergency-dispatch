#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f config/mysql.properties ] && [ -z "${SERC_DB_URL:-}" ]; then
  echo 'Copy config/mysql.properties.example to config/mysql.properties and edit it. See MYSQL_SETUP.md.'
  exit 1
fi
java -cp 'dist/serc.jar:lib/*' serc.WebMain --mysql --init-demo
