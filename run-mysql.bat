@echo off
cd /d "%~dp0"
if not exist config\mysql.properties if not defined SERC_DB_URL (
 echo Copy config\mysql.properties.example to config\mysql.properties and edit it. See MYSQL_SETUP.md.
 pause
 exit /b 1
)
java -cp "dist/serc.jar;lib/*" serc.WebMain --mysql --init-demo
if errorlevel 1 pause
