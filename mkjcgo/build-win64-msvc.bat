@rem Build JCGO Win64 dynamic native libraries and binaries for MS VC++ amd64.
@rem
@rem Prerequisites:
@rem * Microsoft (R) C/C++ Optimizing Compiler Version 15+ for x64
@rem * Microsoft Windows SDK for Windows 7 and .NET Framework 3.5+
@rem * cd <path_to_jcgo>
@rem * (cd contrib; curl -L https://github.com/ivmai/bdwgc/releases/download/v8.2.8/gc-8.2.8.tar.gz | tar zxf -; mv gc-8.2.8 bdwgc)
@rem * (cd contrib/bdwgc; curl -L https://github.com/ivmai/libatomic_ops/releases/download/v7.8.4/libatomic_ops-7.8.4.tar.gz | tar zxf -; mv libatomic_ops-7.8.4 libatomic_ops)
@rem
@rem MSVC environment (cl, lib, INCLUDE, LIB) is loaded automatically
@rem via mkjcgo\vcvars-locate.bat -- no need to run from a VS Developer
@rem Command Prompt. If you already are in one (VCINSTALLDIR is set),
@rem the helper is a no-op.

@call "%~dp0vcvars-locate.bat" x64 || exit /b 1

@set AR=lib
@set CC=cl
@set ARCH=amd64
@set BASESYS=win32
@set SYST=msvc

@rem Build BDWGC static multi-threaded library (gc.lib):
mkdir libs\%ARCH%\%SYST%
mkdir .build_tmp\libs-gc-%ARCH%-%SYST%
cd .build_tmp\libs-gc-%ARCH%-%SYST%
%CC% -Ox -W4 -wd4100 -wd4127 -GF -MT -DALL_INTERIOR_POINTERS -DJAVA_FINALIZATION -DGC_GCJ_SUPPORT -DNO_DEBUGGING -DLARGE_CONFIG -DUSE_MUNMAP -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DPARALLEL_MARK -DDONT_USE_USER32_DLL -I..\..\contrib\bdwgc\include -I..\..\contrib\bdwgc\libatomic_ops\src -D_CRT_SECURE_NO_DEPRECATE -wd4565 -Zl -c ..\..\contrib\bdwgc\*.c ..\..\contrib\bdwgc\*.cpp /nologo || exit /b 1
%AR% /machine:%ARCH% /out:..\..\libs\%ARCH%\%SYST%\gc.lib *.obj /nologo || exit /b 1
cd ..\..

@rem Build BDWGC dynamic library (multi-threaded):
mkdir dlls\%ARCH%\%BASESYS%
mkdir .build_tmp\dlls-gc-%ARCH%-%SYST%
cd .build_tmp\dlls-gc-%ARCH%-%SYST%
%CC% -Ox -W4 -wd4100 -wd4127 -GF -MT -DALL_INTERIOR_POINTERS -DJAVA_FINALIZATION -DGC_GCJ_SUPPORT -DATOMIC_UNCOLLECTABLE -DNO_DEBUGGING -DLARGE_CONFIG -DUSE_MUNMAP -DGC_THREADS -DTHREAD_LOCAL_ALLOC -DPARALLEL_MARK -I..\..\contrib\bdwgc\include -I..\..\contrib\bdwgc\libatomic_ops\src -D_CRT_SECURE_NO_DEPRECATE -wd4565 -DGC_DLL -LD ..\..\contrib\bdwgc\extra\gc.c ..\..\contrib\bdwgc\*.cpp /link /implib:gcdll.lib /out:..\..\dlls\%ARCH%\%BASESYS%\gc64.dll /nologo user32.lib || exit /b 1
copy /Y gcdll.lib ..\..\libs\%ARCH%\%SYST%\ || exit /b 1
cd ..\..

@rem Build winmain (wide-char variant):
cd libs\%ARCH%\%SYST%
%CC% -W3 -MT -DWINMAIN_SETLOCALE -DWINMAIN_WCHAR -Zl -c -Fowwinmain ..\..\..\miscsrc\winmain\winmain.c /nologo || exit /b 1
cd ..\..\..

@rem Test compile jcgon:
mkdir .build_tmp\test-jcgon-%ARCH%-%SYST%
cd .build_tmp\test-jcgon-%ARCH%-%SYST%
%CC% -Ox -W3 -GF -MT -DJCGO_INTNN -DJCGO_FFDATA -DJCGO_LARGEFILE -DJCGO_EXEC -DJCGO_WIN32 -DJCGO_INET -DJCGO_ERRTOLOG -DJCGO_WMAIN -DJCGO_SYSWCHAR -I..\..\include -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE -c ..\..\native\*.c /nologo || exit /b 1
cd ..\..
