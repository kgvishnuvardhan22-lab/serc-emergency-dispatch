@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
javac --release 11 -encoding UTF-8 -cp out -d out src\test\java\serc\*.java
if errorlevel 1 exit /b 1
java -cp out serc.ServiceTest
