@echo off
rem Builds the PETSCIIator desktop app into dist\app\PETSCIIator\PETSCIIator.exe
rem and, if WiX 3 is available (tools\wix or on the PATH), an installer into
rem dist\installer.
rem Needs a JDK 17 or newer (for jpackage). Uses JAVA_HOME if set, otherwise
rem the newest JDK found under "C:\Program Files\Eclipse Adoptium".
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "JDK=%JAVA_HOME%"
if "%JDK%"=="" (
	for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JDK=%%d"
)
if not exist "%JDK%\bin\jpackage.exe" (
	echo No JDK with jpackage found. Install a JDK 17+ or set JAVA_HOME.
	exit /b 1
)
echo Using JDK: %JDK%

if exist build rmdir /s /q build
if exist dist\app rmdir /s /q dist\app
if exist dist\installer rmdir /s /q dist\installer
mkdir build\classes build\input

dir /s /b src\main\java\*.java > build\sources.txt
"%JDK%\bin\javac" --release 8 -nowarn -Xlint:-options -encoding UTF-8 -cp "lib\*" -d build\classes @build\sources.txt || exit /b 1
xcopy /q /y src\main\resources\* build\classes\ > nul

"%JDK%\bin\jar" --create --file build\input\petsciiator.jar --main-class com.sixtyfour.petscii.gui.PetsciiatorApp -C build\classes . || exit /b 1
copy /y lib\*.jar build\input\ > nul

"%JDK%\bin\jpackage" --type app-image --name PETSCIIator --app-version 1.0.0 ^
	--description "Converts images into Commodore PETSCII" --vendor "PETSCIIator" ^
	--icon packaging\petsciiator.ico ^
	--input build\input --main-jar petsciiator.jar --main-class com.sixtyfour.petscii.gui.PetsciiatorApp ^
	--add-modules java.desktop,java.prefs,java.xml,java.logging,java.sql ^
	--jlink-options "--strip-debug --no-header-files --no-man-pages" ^
	--java-options -Xmx2048m ^
	--dest dist\app || exit /b 1

echo App: dist\app\PETSCIIator\PETSCIIator.exe

if exist tools\wix\candle.exe set "PATH=%~dp0tools\wix;%PATH%"
where candle.exe > nul 2>&1
if errorlevel 1 (
	echo No WiX 3 found, skipping the installer. Put the WiX 3 binaries into tools\wix to build it.
	exit /b 0
)

rem The upgrade UUID must never change, otherwise new versions won't replace old ones
"%JDK%\bin\jpackage" --type exe --app-image dist\app\PETSCIIator --name PETSCIIator --app-version 1.0.0 ^
	--description "Converts images into Commodore PETSCII" --vendor "PETSCIIator" ^
	--license-file LICENSE.txt ^
	--win-menu --win-menu-group PETSCIIator --win-shortcut --win-dir-chooser --win-per-user-install ^
	--win-upgrade-uuid 0c05b918-a760-47c8-8ee2-b2031382220b ^
	--dest dist\installer || exit /b 1

echo Installer: dist\installer\PETSCIIator-1.0.0.exe
