#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out dist
javac --release 11 -encoding UTF-8 -d out src/main/java/serc/*.java
cp -R src/main/resources/. out/
jar --create --file dist/serc.jar --main-class serc.WebMain -C out .
echo 'Built dist/serc.jar. Run: java -jar dist/serc.jar'
