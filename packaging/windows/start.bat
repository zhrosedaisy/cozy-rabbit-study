@echo off
setlocal
cd /d "%~dp0"

rem Cozy Rabbit Study needs Java 21 or newer.
where javaw >nul 2>nul
if errorlevel 1 goto nojava

set "JVER="
set "JMAJOR="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i /c:"version"') do (
  if not defined JVER set "JVER=%%~v"
)
for /f "tokens=1 delims=." %%m in ("%JVER%") do set "JMAJOR=%%m"
if not defined JMAJOR set "JMAJOR=0"
if %JMAJOR% LSS 21 goto oldjava

start "" javaw --enable-native-access=ALL-UNNAMED -cp "lib\common\*;lib\win\*" com.cozyrabbit.Launcher %*
exit /b 0

:nojava
echo Java was not found on this computer.
echo.
echo Cozy Rabbit Study needs Java 21 or newer. Install it once, then start the app again:
echo   winget install EclipseAdoptium.Temurin.21.JRE
echo or download Temurin 21 from https://adoptium.net
echo.
pause
exit /b 1

:oldjava
echo The Java on this computer is too old (version %JMAJOR%).
echo.
echo Cozy Rabbit Study needs Java 21 or newer. Install it once, then start the app again:
echo   winget install EclipseAdoptium.Temurin.21.JRE
echo or download Temurin 21 from https://adoptium.net
echo.
pause
exit /b 1
