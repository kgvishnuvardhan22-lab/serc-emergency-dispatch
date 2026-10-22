# SERC - Smart Emergency Response Coordinator

A browser-based emergency dispatch simulation written in pure Java with MySQL
persistence. Citizens report medical, fire and crime emergencies; the system
prioritises them by severity and assigns the nearest available compatible
response team (ambulance, fire truck, police), then tracks each response from
report to resolution.

> **Educational simulation only.** SERC never contacts or dispatches real
> emergency services. All bundled people, contacts and incidents are fake demo
> data. Role selection is a demo convenience, not authentication.

## Features

- **Severity-ordered priority queue** - waiting reports are ranked by severity
  (1 = highest), then report time, then arrival sequence.
- **Nearest compatible dispatch** - assigns the geographically nearest
  available team of the required type; reports whose team type is fully busy
  stay queued without being lost.
- **Full incident lifecycle** - REPORTED -> ASSIGNED -> IN_PROGRESS -> RESOLVED,
  with a per-incident history record (assigned / started / resolved timestamps)
  and admin summary reports.
- **Transactional MySQL persistence** - every confirmed change commits the full
  validated state in one transaction, guarded by an optimistic revision check;
  uncertain commits block further writes instead of lying about success.
- **Automatic backup + recovery** - a serialized snapshot is written after each
  commit and can be opened in a read-only recovery mode (`--recover`).
- **Two interfaces, one engine** - a single-page web GUI served by Java's
  built-in HTTP server (loopback only) and a console menu UI over the same
  thread-safe `DispatchService`.
- **Tested** - 124 automated checks including 100 two-thread race tests, plus
  14 headless-Chrome checks against the packaged server; verified end-to-end
  against real MySQL 8.0.46.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Language | Java 11+ (no web framework, single runnable JAR) |
| GUI      | HTML / CSS / JavaScript served by `com.sun.net.httpserver` on `127.0.0.1:8765` |
| Database | MySQL 8 (InnoDB) via JDBC, MySQL Connector/J 8.4.0 (bundled in `lib/`) |
| Build    | `build.sh` / `build.bat`, `test.sh` / `test.bat`, optional Maven (`pom.xml`) |
| Diagrams | Draw.io (`docs/SERC_Diagrams.drawio`, rendered: `docs/dfd.png`, `docs/usecase.png`) |

## Project structure

```
src/main/java/serc/
  Model.java           - data models, enums, seed data
  DispatchService.java - priority queue, dispatch, lifecycle, validation
  Main.java            - console menu UI (+ concurrency demo)
  WebMain.java         - loopback HTTP server + JSON API
  Store.java           - storage interface (file / in-memory)
  JdbcStore.java       - transactional MySQL snapshot store
  Backup.java          - serialization, snapshot copy, backup save/load
src/main/resources/web/index.html - the single-page web GUI
src/test/java/serc/ServiceTest.java - 124-check acceptance suite
sql/schema.sql, sql/seed.sql        - MySQL schema and demo seed
config/mysql.properties.example     - DB connection template
docs/SERC_Diagrams.drawio, dfd.png, usecase.png - DFD and use case diagrams
lib/mysql-connector-j-8.4.0.jar     - bundled JDBC driver
dist/serc.jar                       - packaged runnable JAR
```

## Quick start (local file demo, no MySQL)

Requires Java 11 or newer. From the project folder:

```
java -jar dist/serc.jar
```

Then open <http://127.0.0.1:8765> in a browser on the same computer. Keep the
terminal running; Ctrl+C stops the server. This mode stores state in
`data/serc_state.dat` and a backup in `data/emergency_backup.dat`, and is
clearly labelled LOCAL FILE DEMO in the app.

## MySQL mode (recommended)

1. Start a local MySQL 8 server and run `sql/schema.sql` once in a fresh
   database (optionally `sql/seed.sql` for demo users and teams).
2. Create an application database user with SELECT/INSERT/UPDATE/DELETE on
   `serc.*`. Never commit credentials.
3. Copy `config/mysql.properties.example` to `config/mysql.properties` and fill
   in `url`, `user`, `password` (or set `SERC_DB_URL`, `SERC_DB_USER`,
   `SERC_DB_PASSWORD` in the environment).
4. Run `run-mysql.bat` (Windows) or `./run-mysql.sh` (macOS/Linux) - see
   `MYSQL_SETUP.md` for details.

## Build and test from source

Windows:

```
build.bat
test.bat
java -jar dist\serc.jar
```

macOS / Linux:

```
sh build.sh
sh test.sh
java -jar dist/serc.jar
```

The test script builds first and runs the full acceptance suite. A Maven
project is included for IDE import; use the scripts for the real checks.

## Web API

All mutation endpoints are POST form requests from the local page only
(Host/Origin checked):

| Endpoint | Action |
|----------|--------|
| `GET /api/state` | Current users, teams, queue, history as JSON |
| `POST /api/report` | File a new emergency report |
| `POST /api/dispatch` | Assign the nearest compatible team |
| `POST /api/start` / `POST /api/resolve` | Lifecycle transitions |
| `POST /api/team/add` / `POST /api/team/remove` | Admin team management |
| `POST /api/backup` | Save the serialized backup |

## Safety notes

- The server binds to loopback only; do not expose it publicly.
- File mode does not support two instances sharing the same data files.
- The transactional full-snapshot store is a teaching design, not built for
  large datasets.

## Team

K. G. Vishnu Vardhan - Jaswanth P. V. - Manoj Kumar - Naveen - Akash R.
