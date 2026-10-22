#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
javac --release 11 -encoding UTF-8 -cp out -d out src/test/java/serc/*.java
java -cp out serc.ServiceTest
