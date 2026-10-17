@echo off
echo Compiling CampusDesk Java files...
if not exist out mkdir out
javac -encoding UTF-8 -d out *.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    exit /b %ERRORLEVEL%
)

set PORT=8080
if not "%~1"=="" set PORT=%~1

echo Starting CampusDesk on http://localhost:%PORT%...
echo Default login: admin / admin123
echo Press Ctrl+C to stop the server.
java -cp out college.registration.Main %PORT%
