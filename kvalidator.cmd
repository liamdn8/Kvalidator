@echo off
REM KValidator - Quick run script for Windows

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%target\kvalidator-1.0.0-SNAPSHOT-jar-with-dependencies.jar

REM Check if JAR exists
if not exist "%JAR_FILE%" (
    echo JAR file not found: %JAR_FILE%
    echo Please build first: mvn clean package
    exit /b 1
)

REM Check if validation-config.yaml exists
if not exist "validation-config.yaml" (
    echo validation-config.yaml not found in current directory
    echo Copying default config...
    if exist "%SCRIPT_DIR%src\main\resources\validation-config.yaml" (
        copy "%SCRIPT_DIR%src\main\resources\validation-config.yaml" .
        echo Copied validation-config.yaml
    )
)

REM Run KValidator
java -jar "%JAR_FILE%" %*
