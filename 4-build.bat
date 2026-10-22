@echo off
cd /d "%~dp0"
if not exist out mkdir out
if not exist dist mkdir dist
javac --release 11 -encoding UTF-8 -d out src\main\java\serc\*.java
if errorlevel 1 exit /b 1
xcopy src\main\resources out /E /I /Y >nul
jar --create --file dist\serc.jar --main-class serc.WebMain -C out .
if errorlevel 1 exit /b 1
echo Built dist\serc.jar. Run: java -jar dist\serc.jar
