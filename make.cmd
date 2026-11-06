@echo off
setlocal enabledelayedexpansion
set PYTHON=C:\Users\user\AppData\Local\Programs\Python\Python312\python.exe

set TARGET=
:parse_args
if "%~1"=="" goto after_parse
echo %~1 | findstr /R "^[A-Za-z0-9_][A-Za-z0-9_]*=.*" >nul
if errorlevel 1 (
    if "!TARGET!"=="" (
        set TARGET=%~1
    )
) else (
    set "%~1"
)
shift
goto parse_args

:after_parse
if "!TARGET!"=="" (
    echo No target specified.
    exit /b 1
)

if "!TARGET!"=="local-up" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" up
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="migrate" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" migrate
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="seed" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" seed
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="run-core" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" run-core
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="run-ai" (
    set PROFILE=ai
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" run-ai
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="local-down" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" down
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="local-config-check" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" validate
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="local-status" (
    "%PYTHON%" "%~dp0scripts\local\dependencies.py" status
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="status-check" (
    "%PYTHON%" "%~dp0scripts\check_repository.py"
    exit /b !ERRORLEVEL!
)
if "!TARGET!"=="cold-start" (
    "%PYTHON%" "%~dp0scripts\local\cold_start.py"
    exit /b !ERRORLEVEL!
)
echo Unknown target: !TARGET!
exit /b 1
