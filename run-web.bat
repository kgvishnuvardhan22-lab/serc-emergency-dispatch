@echo off
cd /d "%~dp0"
echo Open http://127.0.0.1:8765 after the server starts. Keep this window open.
java -jar dist\serc.jar
pause
