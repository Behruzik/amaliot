1@echo off
setlocal
cd /d "%~dp0"
java -version >nul 2>&1
if errorlevel 1 (
  echo Java 17 or newer is required. Install a JDK and reopen this file.
  pause
  exit /b 1
)
echo Starting local demo. Swagger: http://localhost:8080/swagger-ui.html
java -jar "%~dp0dist\Amaliyot.jar" --spring.profiles.active=demo %*
pause
