# Recommended: MySQL-connected website

Follow **MYSQL_SETUP.md** first. Run `run-mysql.bat` or `./run-mysql.sh` after importing the schema and setting `config/mysql.properties`. Connector/J 8.4.0 is bundled in `lib/` with its license. This route was tested against real MySQL 8.0.46. The plain JAR command below remains an optional local-file preview, not MySQL.

# SERC: Smart Emergency Response Coordinator

A browser-based Java mini-project based on the supplied seven-page deck.
Educational simulation only. Never use it to dispatch real emergency services.
All bundled people, contacts and incidents are fake demo data.

## 1. Try the built app first

You need Java 11 or newer on a computer. A phone can download this ZIP, but
running it needs a computer with Java. Extract the whole SERC folder and open
a terminal **inside it**.

    java -version
    java -jar dist/serc.jar

The default mode is explicitly labelled LOCAL FILE DEMO, not MySQL. It creates
`data/serc_state.dat` and `data/emergency_backup.dat` relative to that folder.
Open http://127.0.0.1:8765 in a browser on that same computer. Keep the Java terminal running; Ctrl+C stops the server. Reports are saved after each confirmed change. If a save fails, no success is
shown and live memory is unchanged. Don't run two file-mode instances against
the same files. Don't manually edit or download replacement .dat files.

The website has Citizen, Dispatcher and Admin tabs. In Citizen, choose the
fake citizen, fill the emergency form and click Submit report. Dispatcher
shows the sorted queue, Assign nearest team and active Start/Resolve buttons.
Admin lists/manages teams, shows history, saves backup and exports summary CSV.
The Java server binds only to your computer's loopback address, not the public
Internet. This is a website served by Java, not an installed mobile app.
The tabs are demo role views, not secure logins. Do not expose this server
through a public tunnel or change it to bind 0.0.0.0 without adding security.

## 2. Build and test from source

Install a **JDK**, not only a JRE, for javac and jar. Java 11+ is sufficient;
Java 21 is fine. All five members should agree on a version.

Windows Command Prompt:

    build.bat
    test.bat
    java -jar dist\serc.jar

macOS / Linux:

    sh build.sh
    sh test.sh
    java -jar dist/serc.jar

`test.sh` calls `./build.sh`; if permission was lost when unzipping, run
`chmod +x build.sh test.sh` first. No Maven or external libraries are needed
for the local-file build and acceptance tests. The scripts fail on errors.
A Maven project is also included for IDE import and JDBC dependency handling.
`mvn test` compiles the dependency-free suite but does not automatically run
its main method. Use test.bat / test.sh to run the actual acceptance checks.

## 3. Enable the deck's JDBC/MySQL persistence

Tested against real MySQL 8.0.46 with Connector/J 8.4.0. The simplest route is MYSQL_SETUP.md and run-mysql.bat/run-mysql.sh. The advanced Maven/IDE route below is optional.

1. Install/run a local MySQL server (8.4 recommended). From MySQL Workbench or
   its SQL client, run `sql/schema.sql` once in a fresh demo database. Do not
   use the app's snapshot store on someone else's database.
2. Create a local database user yourself, granting SELECT/INSERT/UPDATE/DELETE
   on `serc.*`. The schema should be created by your setup/admin account.
   Do not put passwords in source code, shell history or the ZIP you submit.
3. Set `SERC_DB_URL` to `jdbc:mysql://localhost:3306/serc`, `SERC_DB_USER` to
   your app database user, and `SERC_DB_PASSWORD` to its password in your IDE's
   private Run Configuration or your terminal environment. Never commit them.
4. In an IDE, import pom.xml as Maven, reload dependencies, run `serc.WebMain`
   with program arguments `--mysql --init-demo`. This seeds demo users and
   teams only when users are empty. It never resets a nonempty database.
5. Or use Maven to download dependencies and package:

       mvn package dependency:copy-dependencies

   Windows:

       java -cp "target/serc-1.0.0.jar;target/dependency/*" serc.WebMain --mysql --init-demo

   macOS / Linux:

       java -cp 'target/serc-1.0.0.jar:target/dependency/*' serc.WebMain --mysql --init-demo

6. Alternatively run `sql/seed.sql` once in a fresh database after the schema,
   then start with `--mysql` without `--init-demo`. On later runs use `--mysql`.

The pinned driver is `com.mysql:mysql-connector-j:8.4.0`, a published Maven
Central artifact. It is **also bundled in lib for the simple MySQL launchers; not embedded** in dist/serc.jar. Use run-mysql for the bundled classpath, or the Maven
runtime classpath above for --mysql. The JAR alone still works for file mode.
Avoid disabling TLS verification to fix connection problems. Consult your
server setup if JDBC reports a TLS or authentication error.

## 4. How the code fits together

- Model.java: enums, encapsulated mutable team/emergency status, User,
  abstract Emergency, three concrete emergency types, Dispatchable,
  ResponseHistory and Serializable Snapshot. requiredTeamType() demonstrates
  overriding and runtime polymorphism. Collections use generics.
- DispatchService.java: one shared lock protects queue/state operations,
  severity ordering, nearest compatible available team, lifecycle, team
  management, validation and custom exceptions. Uses Streams and lambdas.
- Store.java: storage interface plus file and memory implementations.
- JdbcStore.java: PreparedStatements, ResultSets, try-with-resources,
  transactions, rollback and optimistic revision checks.
- Backup.java: serialization, filtered deserialization, data-only snapshots
  and temporary-file replacement. No locks/connections/lambda comparator are
  serialized. PriorityQueue is rebuilt from REPORTED emergencies.
- WebMain.java: loopback Java HTTP server/API and request validation.
- src/main/resources/web/index.html: responsive browser UI (HTML/CSS/JS).
- Main.java: optional console runner retained for learning, not the default.
- ServiceTest.java: dependency-free automatic acceptance tests.

### Deliberate beginner-friendly database tradeoff

JdbcStore writes a complete small simulation snapshot in one transaction,
rather than using separate row-specific repositories. It locks the one
app_metadata row and verifies a revision before delete/reinsert in FK-safe
order. All changes commit or roll back together; memory is updated only
after the store reports success. DELETE is used, never TRUNCATE. This is
simple but slower and inappropriate for large or externally edited datasets.
The extra app_metadata table is an implementation choice, not in the deck.

Run one application instance. The revision check rejects a second stale JDBC
session instead of silently overwriting another session's committed data.
On commit uncertainty, further writes are blocked until restart/reconciliation.
No claims of a distributed dispatch service are made.

## 5. Dispatch rules

Severity 1 is highest, 5 lowest. Ties use report time then a saved sequence.
A medical emergency needs an ambulance, fire needs a fire truck, crime needs
police. Only AVAILABLE compatible teams qualify. Distance is Math.hypot on
simulated x/y coordinates, not real roads or arrival time. Distance ties use
team ID. An unserviceable high-priority report remains waiting while another
type can be dispatched. Active assignments are not preempted.

REPORTED -> ASSIGNED -> IN_PROGRESS -> RESOLVED. Resolution frees the team
and moves it to the incident coordinates. One team per incident, no reassignment.
You cannot delete a busy team or one referenced by history. The queue is never
shown using its unsorted iterator. Snapshot getters return detached data.

The interface returns data but does not enforce secure user authorization.
There is a local browser UI and HTTP API. There is no real login, GPS, notifications, public deployment or medical triage.
Don't present these as implemented features. Avoid forcing an unnecessary
unsafe cast only to mention downcasting; polymorphism is the main OOP mechanism.

## 6. Backup recovery

The independently serialized `data/emergency_backup.dat` is saved on startup,
after confirmed changes and on clean exit, under the same service lock.
A failed backup does not undo a successful primary write: a warning is printed.
The previous backup is retained where replacement can be atomic; a backup
can lag committed data, and atomic-move fallback has weaker crash guarantees.

If the primary store is unavailable, run:

    java -jar dist/serc.jar --recover

This opens the existing backup **read-only**. It never overwrites MySQL with
older data. Corrupt/untrusted backups are rejected, not silently ignored.
Never delete the primary store or backup as a troubleshooting shortcut.
To reset a disposable local demo, first exit and move its entire data folder
aside yourself. The app has no automatic reset or data deletion menu.

## 7. Verification and demonstration

`test.sh` / `test.bat` checks priority/ties, nearest compatible selection,
blocked-head handling, lifecycle, validation, history protection, 100 two-thread
one-ambulance races, persistence restart, backup restore/corruption, read-only
recovery, failed-store memory rollback and detached snapshots. See TEST_RESULTS.md.

Manual website demo:
1. Run java -jar dist/serc.jar and open http://127.0.0.1:8765.
2. Citizen tab: report MEDICAL severity 4 at (0,0), then severity 1.
3. Dispatcher: show severity 1 first, then click Assign nearest team. It picks
   AMB-1 at (3,4), not incompatible POL-1 at (0,1).
4. Click Start response then Resolve response. Admin shows AMB-1 available
   at (0,0) and completed history.
5. Restart the server from the same folder and refresh the browser. Data remains.
6. Stop the server. Run java -jar dist/serc.jar --recover, then reopen/refresh
   the website. Backup records are visible and mutation buttons are disabled.
7. Run test.bat/test.sh for the 100 two-thread races. The browser uses the same
   synchronized service; simultaneous HTTP requests cannot double-book a team.

MySQL follow-up checks (normal lifecycle and restart passed; fault-injection and stale-writer tests are still recommended):
- Import schema and seed; connect with dedicated user and correct classpath.
- Report/dispatch/start/resolve, restart, inspect all four domain tables.
- Force an INSERT failure in a throwaway test database and confirm the whole
  transaction rolls back, without a memory mutation or partial assignment.
- Open two instances, commit in one, then verify the stale one rejects a save.
- Stop the database and check that the app reports the failure without silently
  changing to another store. Check --recover separately for read-only viewing.

## 8. Suggested five-person split

1 Models and validation; 2 Queue and nearest dispatch; 3 Locking/lifecycle/tests;
4 SQL/JDBC; 5 Website, backup and documentation. Each person should understand
all five modules. The supplied project is a starting implementation: run,
read and adapt it before presenting it as your team project.

## References

Project requirements: uploaded SERC deck, especially pages 3–6.
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/PriorityQueue.html
- https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html
- https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
- https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html
- https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html
- https://docs.oracle.com/en/java/javase/25/core/addressing-serialization-vulnerabilities.html

Guide date: 22 October 2026. Code targets Java 11 APIs even where newer API
reference pages describe the same long-standing behavior.

Step-by-step web guide (owner sign-in may be required):
https://files.instinct.com/a1ixczuev8pq-build-serc-step-by-step

## Website-specific notes

Default JAR entry point: serc.WebMain. Only built-in JDK HTTP server, HTML/CSS/JS
and JDBC are used; no Node.js runtime is needed by the user. The browser is a
client of the running Java server, not a fake localStorage-only demo. WebMain
uses a four-thread executor and the existing service lock. UI input is rendered
with textContent, not HTML injection. Mutation requests check Host/Origin, with
no CORS grant; this is baseline local demo safety, not production security.

Open the exact loopback address shown in the terminal. If port 8765 is in use,
stop your previous SERC server first; don't run two file-mode servers against
the same data. There is no automatic public hosting or persistent remote URL.
A classroom web UI expands the deck's planned console interaction, while its
Java dispatch, queue, locking, SQL and backup core remain the same.

The original guide's reference to a console application is superseded by this
web version. Use WebMain and the browser steps above.
