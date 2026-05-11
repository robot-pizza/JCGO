/*
 * @(#) $(JCGO)/include/jcgogmt.c --
 * a part of the JCGO runtime subsystem.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2009 Ivan Maidanski <ivmai@ivmaisoft.com>
 * All rights reserved.
 */

/**
 * This file is compiled together with the files produced by the JCGO
 * translator (do not include and/or compile this file directly).
 */

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 **
 * This software is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License (GPL) for more details.
 **
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 **
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

#ifdef JCGO_VER

JCGO_NOSEP_STATIC jlongArr CFASTCALL
gnu_java_lang_management_VMThreadMXBeanImpl__findDeadlockedThreads0__I(
 jint isMonitorOnly )
{
 /* not implemented */
 return (jlongArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jlong), 0, 0);
}

JCGO_NOSEP_STATIC java_lang_Object CFASTCALL
gnu_java_lang_management_VMThreadMXBeanImpl__getThreadInfoForId0__J(
 jlong id )
{
 /* not implemented */
 return jnull;
}

JCGO_NOSEP_STATIC jlong CFASTCALL
gnu_java_lang_management_VMThreadMXBeanImpl__getThreadCpuUserTime0__JI(
 jlong id, jint isUserTime )
{
 /* not implemented */
 return (jlong)0L;
}

/*
 * Cross-thread stack-walk for ThreadInfo.getStackTrace on a thread
 * other than the caller.
 *
 * Win32 x64: SuspendThread on the target's HANDLE, GetThreadContext
 * to capture its CONTEXT, walk via RtlLookupFunctionEntry +
 * RtlVirtualUnwind (same loop as native/jcgocrash.c), ResumeThread.
 * Returns the captured PCs as a long[] in the same shape
 * VMThrowable.fillInStackTrace0 produces so VMThrowable's existing
 * symbol / line resolution path renders it.
 *
 * Win32 x86: no RtlLookupFunctionEntry / RtlVirtualUnwind — we'd
 * need StackWalk64. Returns null for now; the caller falls back to
 * the empty trace.
 *
 * POSIX: pthread_kill(SIGUSR2) + a sigaction handler running on the
 * target thread that captures its own backtrace into a shared
 * struct. Not yet implemented — returns null.
 *
 * `vmdata` is the Java-side handle on java.lang.VMThread.vmdata,
 * which points into a `struct jcgo_tcb_s` (see jcgothrd.c). The
 * thread's native HANDLE lives at `tcb->thrhandle.handle`.
 */
#if !defined(JCGO_WIN32) && defined(JCGO_THREADS) \
    && !defined(JCGO_NOSTACKTRACECAPTURE)

/*
 * POSIX cross-thread capture protocol.
 *
 * The target thread is asked for its own backtrace by raising
 * SIGUSR2 on it. The handler runs ON THE TARGET, where it calls
 * backtrace() to walk its current call stack, copies the result
 * into a static buffer, and posts a semaphore the caller is
 * waiting on. Single-capture-at-a-time, guarded by a mutex.
 *
 * Constraints:
 *   - backtrace() on glibc is documented async-signal-UNSAFE on
 *     the FIRST call (it lazy-dlopens libgcc). The init function
 *     here warms it up from the main thread so subsequent in-handler
 *     calls are safe.
 *   - dladdr() (used by lookupSymbol0) is generally safe for
 *     post-walk symbol resolution since it runs on the *caller*
 *     thread, not in the signal handler.
 *   - This code is structurally correct but UNTESTED on POSIX. The
 *     Win32 path is what's exercised by JCGO's e2e runs.
 */

#include <pthread.h>
#include <signal.h>
#include <semaphore.h>

/* backtrace lives in <execinfo.h> on glibc; the jcgothrw.c POSIX
 * branch already pulled it in. We forward-declare here to avoid
 * relying on include order. */
extern int backtrace(void **buffer, int size);

#define JCGO_GMT_CAPSIG SIGUSR2

static void *jcgo_gmt_capFrames[JCGO_TRACE_MAX_FRAMES];
static volatile int jcgo_gmt_capCount;
static sem_t jcgo_gmt_capDone;
static pthread_mutex_t jcgo_gmt_capMutex = PTHREAD_MUTEX_INITIALIZER;
static volatile int jcgo_gmt_capInited;

static void jcgo_gmt_capHandler(int sig)
{
 (void)sig;
 jcgo_gmt_capCount = backtrace(jcgo_gmt_capFrames,
                               JCGO_TRACE_MAX_FRAMES);
 if (jcgo_gmt_capCount < 0) jcgo_gmt_capCount = 0;
 (void)sem_post(&jcgo_gmt_capDone);
}

static int jcgo_gmt_capInit(void)
{
 struct sigaction sa;
 if (jcgo_gmt_capInited) return 0;
 if (sem_init(&jcgo_gmt_capDone, 0, 0) != 0) return -1;
 memset(&sa, 0, sizeof(sa));
 sa.sa_handler = jcgo_gmt_capHandler;
 sa.sa_flags = SA_RESTART;
 sigemptyset(&sa.sa_mask);
 if (sigaction(JCGO_GMT_CAPSIG, &sa, NULL) != 0) {
  (void)sem_destroy(&jcgo_gmt_capDone);
  return -1;
 }
 /* Warm up backtrace() so its lazy dlopen runs on the main thread
  * and not from inside the signal handler. */
 {
  void *warm[2];
  (void)backtrace(warm, 2);
 }
 jcgo_gmt_capInited = 1;
 return 0;
}

#endif /* !JCGO_WIN32 && JCGO_THREADS */

JCGO_NOSEP_STATIC java_lang_Object CFASTCALL
gnu_java_lang_management_VMThreadMXBeanImpl__captureThreadStackTrace0__Lo(
 java_lang_Object vmdata )
{
#if defined(JCGO_WIN32) && defined(JCGO_THREADS) \
    && (defined(_M_X64) || defined(__x86_64__))
 struct jcgo_tcb_s *tcb;
 HANDLE handle;
 CONTEXT ctx;
 void *frames[JCGO_TRACE_MAX_FRAMES];
 int count;
 int n;
 jlongArr arr;

 if (vmdata == jnull) return jnull;
 tcb = (struct jcgo_tcb_s *)&JCGO_METHODS_OF(vmdata);
 handle = tcb->thrhandle.handle;
 if (handle == (HANDLE)0
     || handle == (HANDLE)((ptrdiff_t)-1L)
     || handle == (HANDLE)((ptrdiff_t)-2L)) {
  return jnull;
 }

 if (SuspendThread(handle) == (DWORD)-1L) {
  return jnull;
 }
 ZeroMemory(&ctx, sizeof(ctx));
 ctx.ContextFlags = CONTEXT_FULL;
 if (!GetThreadContext(handle, &ctx)) {
  ResumeThread(handle);
  return jnull;
 }

 count = 0;
 for (n = 0; n < JCGO_TRACE_MAX_FRAMES; n++) {
  DWORD64 pc;
  DWORD64 imageBase;
  PRUNTIME_FUNCTION rf;
  PVOID handlerData;
  DWORD64 establisherFrame;

  pc = ctx.Rip;
  if (pc == 0) break;
  frames[count++] = (void *)(size_t)pc;

  imageBase = 0;
  rf = RtlLookupFunctionEntry(pc, &imageBase, NULL);
  if (!rf) {
   /* Leaf frame with no unwind info — fall back to a naive
    * `pop rip` from the suspended RSP. */
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

 ResumeThread(handle);

 arr = (jlongArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jlong), 0,
                               (jint)count);
 if (arr == jnull) return jnull;
 for (n = 0; n < count; n++) {
  JCGO_ARR_INTERNALACC(jlong, arr, n) = (jlong)(size_t)frames[n];
 }
 return (java_lang_Object)arr;
#elif !defined(JCGO_WIN32) && defined(JCGO_THREADS) \
      && !defined(JCGO_NOSTACKTRACECAPTURE)
 struct jcgo_tcb_s *tcb;
 pthread_t target;
 int n;
 int count;
 jlongArr arr;

 if (vmdata == jnull) return jnull;
 tcb = (struct jcgo_tcb_s *)&JCGO_METHODS_OF(vmdata);
 target = tcb->thrhandle;
 if (target == (pthread_t)0) return jnull;

 if (jcgo_gmt_capInit() != 0) return jnull;

 (void)pthread_mutex_lock(&jcgo_gmt_capMutex);
 jcgo_gmt_capCount = 0;
 if (pthread_kill(target, JCGO_GMT_CAPSIG) != 0) {
  (void)pthread_mutex_unlock(&jcgo_gmt_capMutex);
  return jnull;
 }
 /* Wait for the handler to post — with a sanity timeout to avoid
  * deadlock if the target somehow can't deliver the signal. */
 {
  struct timespec ts;
  ts.tv_sec = 1; /* 1s should be ample for the handler */
  ts.tv_nsec = 0;
  if (sem_timedwait(&jcgo_gmt_capDone, &ts) != 0) {
   (void)pthread_mutex_unlock(&jcgo_gmt_capMutex);
   return jnull;
  }
 }
 count = jcgo_gmt_capCount;
 if (count < 0) count = 0;
 if (count > JCGO_TRACE_MAX_FRAMES) count = JCGO_TRACE_MAX_FRAMES;
 arr = (jlongArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jlong), 0,
                               (jint)count);
 if (arr == jnull) {
  (void)pthread_mutex_unlock(&jcgo_gmt_capMutex);
  return jnull;
 }
 for (n = 0; n < count; n++) {
  JCGO_ARR_INTERNALACC(jlong, arr, n) =
   (jlong)(size_t)jcgo_gmt_capFrames[n];
 }
 (void)pthread_mutex_unlock(&jcgo_gmt_capMutex);
 return (java_lang_Object)arr;
#else
 (void)vmdata;
 return jnull;
#endif
}

#endif
