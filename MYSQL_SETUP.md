# Run the Java website connected to MySQL

Tested with a real local MySQL 8.0.46 server and Connector/J 8.4.0 on Linux.
Java 11+ is required. MySQL 8.x must be installed separately and running.
No database password is included. Do not use root as the application account.

1. Extract the whole SERC folder. In MySQL Workbench or the MySQL command-line
   client, connect as your local database administrator.
2. Open and execute `sql/schema.sql` ONCE in a fresh demo database. It creates
   `serc` and five tables (four domain tables plus internal app_metadata).
   Do not run it over an existing project database; it does not erase tables.
3. Create your own local application account, choosing a private password:

   CREATE USER 'serc_app'@'localhost' IDENTIFIED BY 'YOUR_PRIVATE_PASSWORD';
   GRANT SELECT, INSERT, UPDATE, DELETE ON serc.* TO 'serc_app'@'localhost';

4. Copy `config/mysql.properties.example` to `config/mysql.properties`.
   Set the URL host/port, user and password to YOUR database. Local default:

   url=jdbc:mysql://127.0.0.1:3306/serc?connectTimeout=5000&socketTimeout=10000
   user=serc_app
   password=YOUR_PRIVATE_PASSWORD

   This is a Java properties file: literal backslashes must be doubled.
   Keep this file private and out of source control/submissions. Environment
   variables SERC_DB_URL, SERC_DB_USER and SERC_DB_PASSWORD override the file.
5. Run `run-mysql.bat` on Windows, or `./run-mysql.sh` on macOS/Linux.
   The Connector/J driver is included in lib, with its license. No Maven
   download is needed to run. The launcher seeds fake users/teams only when
   the database has no users.
6. Wait for `Mode: MySQL` and open http://127.0.0.1:8765 in your browser.
   The page must show MySQL, not Local file demo. A missing/unreachable
   database or driver stops startup with an error. It never silently uses
   local-file storage in MySQL mode.
7. Report two medical incidents (severity 4 then severity 1), assign a team,
   start and resolve. Close the server with Ctrl+C, then run the same launcher.
   Reports and history must remain. Inspect the tables in Workbench:

   SELECT * FROM serc.emergencies;
   SELECT * FROM serc.response_teams;
   SELECT * FROM serc.response_history;

Troubleshooting:
- Access denied: verify your local MySQL account/password and host grant.
- Communications link failure: start MySQL and check the host/port.
- Unknown database/missing table: execute sql/schema.sql in a fresh database.
- No suitable driver: keep lib/mysql-connector-j-8.4.0.jar beside dist.
- Port 8765 in use: stop the earlier SERC server. Run one app instance only.
- A save error freezes writes. Stop, inspect the database and restart to
  reconcile. Never retry a payment-like or otherwise important action blindly.
- Do not fix authentication/TLS failures by disabling certificate verification.

This educational full-snapshot JDBC store is transactional and checks a
metadata revision to reject stale writers. It is for a small single-instance
simulation, not an externally edited or large production database. Role tabs
are not secure login. Java serves only loopback; no public backend is hosted.

Alternative offline preview: `java -jar dist/serc.jar` uses local files only.
The hosted browser demo is JavaScript only and does not connect to MySQL.
