@REM Maven Wrapper for Windows
@REM Requires Maven to be installed or will download it

@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

for /f "usebackq tokens=1,* delims==" %%a in ("%MAVEN_WRAPPER_PROPERTIES%") do (
    if "%%a"=="distributionUrl" set "DISTRIBUTION_URL=%%b"
)

if not defined DISTRIBUTION_URL set "DISTRIBUTION_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

set "MAVEN_HOME=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\dists"
set "MAVEN_DIR=%MAVEN_HOME%\apache-maven-3.9.9"

if not exist "%MAVEN_DIR%\bin\mvn.cmd" (
    echo Downloading Maven from %DISTRIBUTION_URL% ...
    if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
    powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%MAVEN_HOME%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%' -Force"
    del "%MAVEN_HOME%\maven.zip"
    echo Maven downloaded successfully.
)

"%MAVEN_DIR%\bin\mvn.cmd" %*
