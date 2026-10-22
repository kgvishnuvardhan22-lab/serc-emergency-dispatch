# Verification record

22 October 2026. Linux, OpenJDK 11.0.30; compiled with --release 11.

```
Built dist/serc.jar. Run: java -jar dist/serc.jar
PASS: 124 checks, including 100 two-thread race tests. No live MySQL test in this suite.
```

Also exercised the packaged JAR from a fresh working folder through its console:
- Created severity 4 then severity 1 medical reports.
- Queue displayed severity 1 first; dispatch assigned it to AMB-1.
- Admin concurrent demo printed one assignment, one waiting report and PASS.
- Restart preserved active response history.
- --recover loaded the backup and rejected a new report as read-only.

Lifecycle start/resolve, backup corruption and failed-store memory rollback were
covered by the automated suite. No real emergency was submitted or contacted.

Maven build and Windows execution remain unverified. The core suite itself does not use MySQL. The separate MySQL test below does.

## Web version verification

After changing the default entry point to WebMain, the same 124-check core
suite passed again. A real headless Chrome browser against the packaged Java
server passed 14 additional checks:
- Reporting twice with the website form.
- Sorted priority display, nearest compatible assignment and start/resolve.
- Admin team creation and backup save.
- No horizontal page overflow for all three roles at 320 and 390 pixels.
- No JavaScript exceptions and rejection of a no-Origin mutation request.

All three role views were visually inspected at 320, 390 and 1280 pixels.
The server binds 127.0.0.1:8765 only. No public backend was deployed.

## Real MySQL integration

MySQL Community Server 8.0.46, local loopback port 3307, Connector/J 8.4.0.
Imported sql/schema.sql into a fresh database and used an application account
limited to SELECT/INSERT/UPDATE/DELETE. Six checks passed via the Java HTTP API:
seed/startup, two committed reports, priority plus nearest compatible dispatch,
start/resolve, restart persistence including a new team/history, serialized backup.
Queried emergencies, response_teams and response_history directly in the MySQL
client and confirmed the resolved incident, freed team and lifecycle timestamps.
No user database, credentials or real incident data was used.

Verified bundled driver/config and run-mysql.sh startup; Chrome rendered the MySQL mode and persisted history.

Startup against an unreachable MySQL port was verified to stop with an actionable error and no file-mode fallback. Final packaged Java build passed all 14 browser checks again from a clean data folder.
