@echo off
set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%kvalidator.jar

if not exist "%JAR_FILE%" (
    echo JAR file not found: %JAR_FILE%
    exit /b 1
)

if not exist "validation-config.yaml" (
    echo validation-config.yaml not found, copying default...
    copy "%SCRIPT_DIR%validation-config.yaml" .
)

java -jar "%JAR_FILE%" %*
