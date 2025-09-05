@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ==========================================================
REM  Project Zomboid Java class patcher - RCONServer only
REM  - Compiles src\zombie\network\RCONServer.java with Java 17
REM  - Backs up existing .class files
REM  - Copies new .class files into media\java\zombie\network\
REM ==========================================================

REM --- Config (edit if needed) ---
set "CLASS_PKG=zombie\network"
set "CLASS_NAME=RCONServer"
set "SRC_DIR=."
set "OUT_DIR=out"
set "GAME_CLASSES=c:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid"
set JAVAC="c:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid\jre\bin\javac.exe"
set "JAVA_RELEASE=17"

REM --- Resolve paths relative to this script's folder ---
pushd "%~dp0" 1>nul

REM --- Check javac (prefer the game's bundled one) ---
if not exist "%JAVAC%" (
  echo [!] %JAVAC% not found. Falling back to system javac in PATH...
  set "JAVAC=javac.exe"
)

REM --- Verify source file exists ---
set "SRC_FILE=%SRC_DIR%\%CLASS_PKG%\%CLASS_NAME%.java"
if not exist "%SRC_FILE%" (
  echo [X] Source not found: "%SRC_FILE%"
  echo     Expected: %CD%\%SRC_FILE%
  goto :fail
)

REM --- Create output folder ---
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%" 1>nul

echo.
echo === Compiling %CLASS_PKG%\%CLASS_NAME%.java with --release %JAVA_RELEASE% ===
echo Command:
echo   "%JAVAC%" --release %JAVA_RELEASE% -cp "%GAME_CLASSES%" -d "%OUT_DIR%" "%SRC_FILE%"
echo.

"%JAVAC%" --release %JAVA_RELEASE% -cp "%GAME_CLASSES%" -d "%OUT_DIR%" "%SRC_FILE%"
if errorlevel 1 (
  echo [X] Compilation failed.
  goto :fail
)

REM --- Check compiled output ---
set "OUT_CLASS_DIR=%OUT_DIR%\%CLASS_PKG%"
if not exist "%OUT_CLASS_DIR%\%CLASS_NAME%.class" (
  echo [X] Compiled class not found in: "%OUT_CLASS_DIR%"
  goto :fail
)

REM --- Make a timestamped backup folder ---
for /f %%a in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%a"
set "BACKUP_DIR=backups\%TS%\%CLASS_PKG%"
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%" 1>nul

REM --- Backup existing game classes (main + inner/anonymous) ---
set "GAME_CLASS_DIR=%GAME_CLASSES%\%CLASS_PKG%"
echo.
echo === Backing up existing classes ===
if exist "%GAME_CLASS_DIR%\%CLASS_NAME%*.class" (
  echo From: "%GAME_CLASS_DIR%\%CLASS_NAME%*.class"
  echo   To: "%BACKUP_DIR%"
  copy /Y "%GAME_CLASS_DIR%\%CLASS_NAME%*.class" "%BACKUP_DIR%" 1>nul
) else (
  echo [i] No existing %CLASS_NAME%*.class found to back up.
)

REM --- Deploy compiled classes ---
echo.
echo === Deploying new classes ===
echo From: "%OUT_CLASS_DIR%\%CLASS_NAME%*.class"
echo   To: "%GAME_CLASS_DIR%"
if not exist "%GAME_CLASS_DIR%" mkdir "%GAME_CLASS_DIR%" 1>nul
copy /Y "%OUT_CLASS_DIR%\%CLASS_NAME%*.class" "%GAME_CLASS_DIR%" >nul
if errorlevel 1 (
  echo [X] Copy failed.
  goto :fail
)

echo.
echo [✔] Success! Patched classes copied to:
echo     %GAME_CLASS_DIR%
echo     Backup created at:
echo     %BACKUP_DIR%

echo.
echo Tip: Add a quick System.out.println in your code to confirm load in console.txt
goto :end

:fail
echo.
echo [!] Build script aborted due to an error.

:end
popd 1>nul
endlocal
