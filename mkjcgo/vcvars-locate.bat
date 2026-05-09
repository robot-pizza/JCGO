@rem Auto-load MSVC environment (cl, lib, link, INCLUDE, LIB, ...) for the
@rem requested arch so the build can run from any shell, not only from a
@rem VS Developer Command Prompt.
@rem
@rem Usage:  call mkjcgo\vcvars-locate.bat <x86|x64>
@rem
@rem No-op if VCINSTALLDIR is already defined (caller is already inside a
@rem VS Dev Prompt). Otherwise locates the latest VS install via
@rem vswhere.exe (ships with the VS Installer since VS 2017) and calls
@rem the matching vcvars{32,64}.bat.
@rem
@rem Sets exit code to 1 with a clear error if VS / vswhere isn't found.

@if defined VCINSTALLDIR exit /b 0

@set "_ARCH=%~1"
@if "%_ARCH%"=="" goto :usage
@if /i "%_ARCH%"=="x86" (set "_VCVARS_NAME=vcvars32.bat") else if /i "%_ARCH%"=="x64" (set "_VCVARS_NAME=vcvars64.bat") else goto :badarch

@set "_VSWHERE_DIR=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer"
@if not exist "%_VSWHERE_DIR%\vswhere.exe" set "_VSWHERE_DIR=%ProgramFiles%\Microsoft Visual Studio\Installer"
@if not exist "%_VSWHERE_DIR%\vswhere.exe" goto :no_vswhere
@set "_VSWHERE=%_VSWHERE_DIR%\vswhere.exe"
@rem vcvars{32,64}.bat probes vswhere internally without an absolute path,
@rem so prepend the installer dir so its probe succeeds and we don't get
@rem a noisy "vswhere.exe is not recognized" line during the call.
@set "PATH=%_VSWHERE_DIR%;%PATH%"

@rem Run vswhere via redirect-to-file rather than `for /f` -- the latter
@rem mis-parses when the vswhere path contains the literal `(x86)` parens
@rem because cmd's for /f command-block lexer counts parens naively.
@set "_VSPATH_FILE=%TEMP%\_jcgo_vspath.txt"
@"%_VSWHERE%" -latest -property installationPath > "%_VSPATH_FILE%" 2>nul
@if errorlevel 1 (
    del "%_VSPATH_FILE%" >nul 2>&1
    echo ERROR: vswhere.exe failed.
    exit /b 1
)
@set "_VSPATH="
@set /p _VSPATH=<"%_VSPATH_FILE%"
@del "%_VSPATH_FILE%" >nul 2>&1
@if not defined _VSPATH (
    echo ERROR: vswhere.exe found but no Visual Studio installation detected.
    exit /b 1
)

@set "_VCVARS=%_VSPATH%\VC\Auxiliary\Build\%_VCVARS_NAME%"
@if not exist "%_VCVARS%" (
    echo ERROR: vcvars script not found at "%_VCVARS%".
    exit /b 1
)

@call "%_VCVARS%" >nul
@if errorlevel 1 exit /b %errorlevel%
@exit /b 0

:usage
@echo ERROR: vcvars-locate.bat requires an arch argument ^(x86 or x64^).
@exit /b 1

:badarch
@echo ERROR: vcvars-locate.bat: unknown arch "%_ARCH%" ^(expected x86 or x64^).
@exit /b 1

:no_vswhere
@echo ERROR: vswhere.exe not found. Install Visual Studio 2017+ ^(any edition,
@echo including Build Tools^) or run from a VS Developer Command Prompt.
@exit /b 1
