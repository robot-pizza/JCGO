/*
 * @(#) $(JCGO)/native/jcgocrash.c --
 * Win32 crash-dump installer for JCGO-translated binaries.
 *
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * Adapted from the mobileui editor's crash_dump.c (same author/owner).
 * Strips the AIBridge socket integration; otherwise the symbolization
 * path (RtlVirtualUnwind + SymFromAddr + SymGetLineFromAddr64) is
 * unchanged.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

/*
 * Top-level unhandled-exception filter — when a JCGO-translated binary
 * SIGSEGVs (or any unhandled SEH exception fires), walks the faulting
 * thread's stack with DbgHelp and writes a symbolized text trace next
 * to the executable as `<exe-basename>.stk`.
 *
 * The handler installs via a CRT initializer (.CRT$XCU on MSVC,
 * __attribute__((constructor)) on gcc) so it runs before main; no
 * per-app init call needed. Build needs `/Zi` (MSVC) or `-g` (gcc) so
 * the symbols the walker resolves are useful (function name + file +
 * line); link adds dbghelp.lib / -ldbghelp.
 *
 * Useful in two distinct ways:
 *   1. Real crashes — get a symbolized trace into a .stk file.
 *   2. Verifying the JCGO #line / PDB chain — deliberately crash a
 *      test binary; the dumped frames show the chain works
 *      end-to-end without needing the binary's normal exit path.
 */

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN 1
#endif
#include <windows.h>
#include <dbghelp.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <string.h>

/* The CONTEXT record's instruction/stack-pointer fields are arch-named
 * (Rip/Rsp on x64, Eip/Esp on x86). RtlLookupFunctionEntry and
 * RtlVirtualUnwind only exist on x64, so the full stack walk below
 * is x64-only -- the x86 path falls back to recording just the
 * faulting frame. */
#if defined(_M_X64) || defined(_M_AMD64) || defined(_WIN64)
# define JCGO_CRASH_HAS_UNWIND 1
# define JCGO_CRASH_IP(c)      ((c).Rip)
# define JCGO_CRASH_SP(c)      ((c).Rsp)
#elif defined(_M_IX86) || defined(_M_I386) || defined(_X86_)
# define JCGO_CRASH_HAS_UNWIND 0
# define JCGO_CRASH_IP(c)      ((DWORD64)(c).Eip)
# define JCGO_CRASH_SP(c)      ((DWORD64)(c).Esp)
#else
# error "jcgocrash.c: unsupported architecture"
#endif

static const char *jcgo_crash_exception_name(DWORD code)
{
    switch (code) {
    case EXCEPTION_ACCESS_VIOLATION:        return "ACCESS_VIOLATION";
    case EXCEPTION_DATATYPE_MISALIGNMENT:   return "DATATYPE_MISALIGNMENT";
    case EXCEPTION_BREAKPOINT:              return "BREAKPOINT";
    case EXCEPTION_SINGLE_STEP:             return "SINGLE_STEP";
    case EXCEPTION_ARRAY_BOUNDS_EXCEEDED:   return "ARRAY_BOUNDS_EXCEEDED";
    case EXCEPTION_FLT_DIVIDE_BY_ZERO:      return "FLT_DIVIDE_BY_ZERO";
    case EXCEPTION_FLT_INVALID_OPERATION:   return "FLT_INVALID_OPERATION";
    case EXCEPTION_INT_DIVIDE_BY_ZERO:      return "INT_DIVIDE_BY_ZERO";
    case EXCEPTION_INT_OVERFLOW:            return "INT_OVERFLOW";
    case EXCEPTION_PRIV_INSTRUCTION:        return "PRIV_INSTRUCTION";
    case EXCEPTION_IN_PAGE_ERROR:           return "IN_PAGE_ERROR";
    case EXCEPTION_ILLEGAL_INSTRUCTION:     return "ILLEGAL_INSTRUCTION";
    case EXCEPTION_NONCONTINUABLE_EXCEPTION:return "NONCONTINUABLE";
    case EXCEPTION_STACK_OVERFLOW:          return "STACK_OVERFLOW";
    case EXCEPTION_INVALID_DISPOSITION:     return "INVALID_DISPOSITION";
    case EXCEPTION_GUARD_PAGE:              return "GUARD_PAGE";
    case EXCEPTION_INVALID_HANDLE:          return "INVALID_HANDLE";
    default:                                return "UNKNOWN";
    }
}

static void jcgo_crash_build_stk_path(char *out, size_t cap)
{
    char exe[MAX_PATH] = {0};
    const char *base;
    const char *p;
    char *dot;
    GetModuleFileNameA(NULL, exe, sizeof(exe));
    base = exe;
    for (p = exe; *p; p++) if (*p == '\\' || *p == '/') base = p + 1;
    snprintf(out, cap, "%s", base);
    dot = strrchr(out, '.');
    if (dot) strcpy(dot, ".stk");
    else     strncat(out, ".stk", cap - strlen(out) - 1);
}

static void jcgo_crash_dump(EXCEPTION_POINTERS *ep)
{
    char path[MAX_PATH];
    FILE *f;
    HMODULE mod;
    DWORD64 modBase;
    char head[512];
    DWORD code;
    void *addr;
    HANDLE proc;
    CONTEXT ctx;
    char buf[1024];
    char symBuf[sizeof(SYMBOL_INFO) + 512];
    SYMBOL_INFO *sym;
    int n;
    DWORD64 prev_pc;
    int prev_count;

    jcgo_crash_build_stk_path(path, sizeof(path));
    f = fopen(path, "w");
    if (f) fprintf(stderr, "[jcgo_crash] writing %s\n", path);
    else   fprintf(stderr, "[jcgo_crash] fopen(%s) failed\n", path);
    fflush(stderr);

    mod = GetModuleHandleA(NULL);
    modBase = (DWORD64)mod;
    code = ep->ExceptionRecord->ExceptionCode;
    addr = ep->ExceptionRecord->ExceptionAddress;
    snprintf(head, sizeof(head),
             "ERROR crash %s (0x%08lX) at %p (offset 0x%llx)\n"
             "Module base = 0x%llx\n",
             jcgo_crash_exception_name(code),
             (unsigned long)code, addr,
             (unsigned long long)((DWORD64)addr - modBase),
             (unsigned long long)modBase);
    if (f) fputs(head, f);
    fputs(head, stderr);

    proc = GetCurrentProcess();
    SymSetOptions(SymGetOptions() | SYMOPT_LOAD_LINES
                                  | SYMOPT_DEFERRED_LOADS
                                  | SYMOPT_UNDNAME);
    SymInitialize(proc, NULL, TRUE);

    if (f) fputs("Frames (PC | offset-from-base | symbol):\n", f);
    fputs("Frames (PC | offset-from-base | symbol):\n", stderr);
    ctx = *ep->ContextRecord;
    sym = (SYMBOL_INFO *)symBuf;
    prev_pc = 0;
    prev_count = 0;
    for (n = 0; n < 4096; n++) {
        DWORD64 pc;
        const char *symName;
        DWORD64 disp;
        char modName[MAX_PATH];
        char lineInfo[256];
        IMAGEHLP_LINE64 line;
        DWORD lineDisp;
        IMAGEHLP_MODULE64 mi;

        pc = JCGO_CRASH_IP(ctx);
        if (pc == 0) break;
        symName = "?";
        disp = 0;
        modName[0] = '?';
        modName[1] = 0;
        lineInfo[0] = 0;

        ZeroMemory(sym, sizeof(symBuf));
        sym->SizeOfStruct = sizeof(SYMBOL_INFO);
        sym->MaxNameLen = 511;
        if (SymFromAddr(proc, pc, &disp, sym)) {
            symName = sym->Name;
        }
        ZeroMemory(&line, sizeof(line));
        line.SizeOfStruct = sizeof(line);
        lineDisp = 0;
        if (SymGetLineFromAddr64(proc, pc, &lineDisp, &line)) {
            snprintf(lineInfo, sizeof(lineInfo), " (%s:%lu)",
                     line.FileName, (unsigned long)line.LineNumber);
        }
        ZeroMemory(&mi, sizeof(mi));
        mi.SizeOfStruct = sizeof(mi);
        if (SymGetModuleInfo64(proc, pc, &mi)) {
            snprintf(modName, sizeof(modName), "%s", mi.ModuleName);
        }
        if (pc == prev_pc) {
            prev_count++;
        } else {
            if (prev_count > 0) {
                snprintf(buf, sizeof(buf),
                  "        ... previous frame repeated %d more time(s) ...\n",
                  prev_count);
                if (f) fputs(buf, f);
                fputs(buf, stderr);
            }
            snprintf(buf, sizeof(buf),
                     "  #%-4d 0x%llx  +0x%llx  %s!%s+0x%llx%s\n",
                     n,
                     (unsigned long long)pc,
                     (unsigned long long)(pc - modBase),
                     modName, symName,
                     (unsigned long long)disp, lineInfo);
            if (f) fputs(buf, f);
            fputs(buf, stderr);
            prev_pc = pc;
            prev_count = 0;
        }

#if JCGO_CRASH_HAS_UNWIND
        {
            DWORD64 imageBase = 0;
            PRUNTIME_FUNCTION rf;
            PVOID handlerData;
            DWORD64 establisherFrame;
            rf = RtlLookupFunctionEntry(pc, &imageBase, NULL);
            if (!rf) {
                DWORD64 *rsp = (DWORD64 *)ctx.Rsp;
                if (!rsp) break;
                ctx.Rip = *rsp;
                ctx.Rsp += 8;
                if (ctx.Rip == 0) break;
                continue;
            }
            handlerData = NULL;
            establisherFrame = 0;
            RtlVirtualUnwind(0 /* UNW_FLAG_NHANDLER */, imageBase, pc, rf,
                             &ctx, &handlerData, &establisherFrame, NULL);
            if (ctx.Rip == 0) break;
        }
#else
        /* x86: stop after the first frame. RtlLookupFunctionEntry /
         * RtlVirtualUnwind aren't available; a StackWalk64 path could
         * be added here later if x86 builds need richer traces. */
        break;
#endif
    }
    SymCleanup(proc);
    fflush(stderr);
    if (f) fclose(f);
}

static LONG WINAPI jcgo_crash_filter(EXCEPTION_POINTERS *ep)
{
    fprintf(stderr, "[jcgo_crash] invoked code=0x%08lX\n",
            (unsigned long)ep->ExceptionRecord->ExceptionCode);
    fflush(stderr);
    jcgo_crash_dump(ep);
    return EXCEPTION_EXECUTE_HANDLER;
}

static LONG WINAPI jcgo_crash_vectored(EXCEPTION_POINTERS *ep)
{
    DWORD code = ep->ExceptionRecord->ExceptionCode;
    switch (code) {
    case EXCEPTION_ACCESS_VIOLATION:
    case EXCEPTION_ARRAY_BOUNDS_EXCEEDED:
    case EXCEPTION_DATATYPE_MISALIGNMENT:
    case EXCEPTION_FLT_DIVIDE_BY_ZERO:
    case EXCEPTION_FLT_INVALID_OPERATION:
    case EXCEPTION_ILLEGAL_INSTRUCTION:
    case EXCEPTION_INT_DIVIDE_BY_ZERO:
    case EXCEPTION_PRIV_INSTRUCTION:
    case EXCEPTION_STACK_OVERFLOW:
    case EXCEPTION_NONCONTINUABLE_EXCEPTION:
        jcgo_crash_filter(ep);
        ExitProcess(3);
        return EXCEPTION_CONTINUE_SEARCH;
    default:
        return EXCEPTION_CONTINUE_SEARCH;
    }
}

static void jcgo_crash_abort_signal(int sig)
{
    CONTEXT ctx;
    EXCEPTION_RECORD er;
    EXCEPTION_POINTERS ep;
    fprintf(stderr, "[jcgo_crash] SIGABRT/SIGSEGV (%d)\n", sig);
    fflush(stderr);
    ZeroMemory(&ctx, sizeof(ctx));
    ctx.ContextFlags = CONTEXT_FULL;
    RtlCaptureContext(&ctx);
    ZeroMemory(&er, sizeof(er));
    er.ExceptionCode = (DWORD)0xC0000027;
    er.ExceptionAddress = (PVOID)(SIZE_T)JCGO_CRASH_IP(ctx);
    ep.ExceptionRecord = &er;
    ep.ContextRecord = &ctx;
    jcgo_crash_filter(&ep);
    _exit(3);
}

static void jcgo_crash_install(void)
{
    SetUnhandledExceptionFilter(jcgo_crash_filter);
    AddVectoredExceptionHandler(1 /* CALL_FIRST */, jcgo_crash_vectored);
    signal(SIGABRT, jcgo_crash_abort_signal);
    signal(SIGSEGV, jcgo_crash_abort_signal);
#ifdef _MSC_VER
    _set_abort_behavior(0, _WRITE_ABORT_MSG | _CALL_REPORTFAULT);
#endif
}

#ifdef _MSC_VER
#pragma section(".CRT$XCU", read)
__declspec(allocate(".CRT$XCU"))
void (*p_jcgo_crash_install)(void) = jcgo_crash_install;
#else
__attribute__((constructor))
static void jcgo_crash_run_install(void) { jcgo_crash_install(); }
#endif
