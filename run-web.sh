#!/bin/sh
set -eu
cd "$(dirname "$0")"
echo 'Open http://127.0.0.1:8765 after the server starts. Keep this terminal open.'
java -jar dist/serc.jar
