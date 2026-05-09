# Top-level entry point for JCGO build deliverables.
# Wraps the per-platform scripts under mkjcgo/.

.PHONY: all win32-msvc win64-msvc jcgo-jar zip release \
        clean clean-win32-msvc clean-win64-msvc clean-zip help

all: win32-msvc win64-msvc

win32-msvc:
	cmd /c "mkjcgo\build-win32-msvc.bat"

win64-msvc:
	cmd /c "mkjcgo\build-win64-msvc.bat"

# Build the translator .jars (jcgo.jar + auxbin/jre/*.jar). Pure
# Java; no arch distinction. Runs entirely in PowerShell so no bash
# is required. mkjcgo/build-java.sh remains for the full chain
# (rflg_out + translated C output) on Unix-like hosts.
jcgo-jar:
	powershell -NoProfile -ExecutionPolicy Bypass -File mkjcgo\build-jars.ps1

# Package the release. Stages artifacts under dist/jcgo-binaries-windows/
# and zips them. Doesn't depend on the build targets — verifies inputs
# exist and errors with guidance if not. Use `make release` for the
# end-to-end build + package flow.
zip:
	powershell -NoProfile -ExecutionPolicy Bypass -File mkjcgo\zip-release.ps1

# End-to-end: build everything, then zip.
release: jcgo-jar all zip

clean: clean-win32-msvc clean-win64-msvc clean-zip

clean-win32-msvc:
	cmd /c "if exist libs\x86\msvc rmdir /s /q libs\x86\msvc"
	cmd /c "if exist dlls\x86\win32 rmdir /s /q dlls\x86\win32"
	cmd /c "if exist .build_tmp\libs-gc-x86-msvc rmdir /s /q .build_tmp\libs-gc-x86-msvc"
	cmd /c "if exist .build_tmp\dlls-gc-x86-msvc rmdir /s /q .build_tmp\dlls-gc-x86-msvc"
	cmd /c "if exist .build_tmp\dlls-tinygc-x86-msvc rmdir /s /q .build_tmp\dlls-tinygc-x86-msvc"
	cmd /c "if exist .build_tmp\test-jcgon-x86-msvc rmdir /s /q .build_tmp\test-jcgon-x86-msvc"

clean-win64-msvc:
	cmd /c "if exist libs\amd64\msvc rmdir /s /q libs\amd64\msvc"
	cmd /c "if exist dlls\amd64\win32 rmdir /s /q dlls\amd64\win32"
	cmd /c "if exist .build_tmp\libs-gc-amd64-msvc rmdir /s /q .build_tmp\libs-gc-amd64-msvc"
	cmd /c "if exist .build_tmp\dlls-gc-amd64-msvc rmdir /s /q .build_tmp\dlls-gc-amd64-msvc"
	cmd /c "if exist .build_tmp\test-jcgon-amd64-msvc rmdir /s /q .build_tmp\test-jcgon-amd64-msvc"

clean-zip:
	cmd /c "if exist dist rmdir /s /q dist"

help:
	@echo Targets:
	@echo   all (default)     Build both win32-msvc and win64-msvc.
	@echo   win32-msvc        Build x86 (32-bit) MSVC runtime libs and DLLs.
	@echo                     Output: libs/x86/msvc/, dlls/x86/win32/
	@echo   win64-msvc        Build amd64 (64-bit) MSVC runtime libs and DLLs.
	@echo                     Output: libs/amd64/msvc/, dlls/amd64/win32/
	@echo   jcgo-jar          Build jcgo.jar + auxbin/jre/*.jar (translator).
	@echo                     Pure PowerShell; needs javac/jar on PATH.
	@echo   zip               Package dist/jcgo-binaries-windows.zip from
	@echo                     existing build artifacts.
	@echo   release           jcgo-jar + all + zip (end-to-end).
	@echo   clean             Remove all build + dist outputs.
	@echo   clean-win32-msvc  Remove x86 MSVC build outputs.
	@echo   clean-win64-msvc  Remove amd64 MSVC build outputs.
	@echo   clean-zip         Remove dist/.
	@echo.
	@echo Prerequisites:
	@echo   - Visual Studio 2017+ installed (any edition, incl. Build Tools).
	@echo     vcvars is auto-loaded via mkjcgo/vcvars-locate.bat; no need
	@echo     to run from a VS Developer Command Prompt.
	@echo   - JDK with javac/jar on PATH (for jcgo-jar).
	@echo   - contrib/bdwgc/, contrib/bdwgc/libatomic_ops/, contrib/tinygc/
	@echo     unpacked (see mkjcgo/build-win{32,64}-msvc.bat headers).
