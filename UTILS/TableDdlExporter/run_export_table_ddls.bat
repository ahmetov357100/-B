@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "CP=C:\oracle\sqlcl\lib\ojdbc11.jar;C:\oracle\sqlcl\lib\orai18n.jar;%SCRIPT_DIR%"
set "TABLE_LIST=%SCRIPT_DIR%\table_list.txt"
set "CONNECTION_FILE=%SCRIPT_DIR%\tnsnames.txt"
set "OUTPUT_DIR=%SCRIPT_DIR%"

if not "%~1"=="" set "TABLE_LIST=%~1"
if not "%~2"=="" set "CONNECTION_FILE=%~2"
if not "%~3"=="" set "OUTPUT_DIR=%~3"

java -cp "%CP%" ExportOracleTableDdlsFromList "%TABLE_LIST%" "%CONNECTION_FILE%" "%OUTPUT_DIR%"

exit /b %ERRORLEVEL%
