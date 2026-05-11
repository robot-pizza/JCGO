/*
 * @(#) $(JCGO)/goclsp/vm/gnu/java/lang/management/VMThreadMXBeanImpl.java --
 * VM specific thread bean implementation.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2008 Ivan Maidanski <ivmai@ivmaisoft.com>
 * All rights reserved.
 **
 * Class specification origin: GNU Classpath v0.93 vm/reference
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

package gnu.java.lang.management;

import java.lang.management.ThreadInfo;
import java.lang.reflect.Constructor;

final class VMThreadMXBeanImpl
{

 private VMThreadMXBeanImpl() {}

 static long[] findDeadlockedThreads()
 {
  return findDeadlockedThreads0(0);
 }

 static long[] findMonitorDeadlockedThreads()
 {
  return findDeadlockedThreads0(1);
 }

 static long[] getAllThreadIds()
 {
  Thread[] threads = getAllThreads();
  int i = threads.length;
  while (i-- > 0)
   if (threads[i] != null)
    break;
  long[] ids = new long[i + 1];
  if (i >= 0)
   do
   {
    ids[i] = threads[i].getId();
   } while (--i >= 0);
  return ids;
 }

 static long getCurrentThreadCpuTime()
 {
  return getThreadCpuTime(Thread.currentThread().getId());
 }

 static long getCurrentThreadUserTime()
 {
  return getThreadUserTime(Thread.currentThread().getId());
 }

 static int getDaemonThreadCount()
 {
  Thread[] threads = getAllThreads();
  int count = 0;
  Thread t;
  for (int i = 0; (t = threads[i]) != null; i++)
   if (t.isDaemon())
    count++;
  return count;
 }

 static void getLockInfo(ThreadInfo info)
 {
  /* not implemented */
 }

 static void getMonitorInfo(ThreadInfo info)
 {
  /* not implemented */
 }

 static int getPeakThreadCount()
 {
  return VMAccessorJavaLang.getPeakThreadCountVMThread(false);
 }

 static int getThreadCount()
 {
  Thread[] threads = getAllThreads();
  int i = threads.length;
  while (i-- > 0)
   if (threads[i] != null)
    break;
  return i + 1;
 }

 static long getThreadCpuTime(long id)
 {
  return getThreadCpuUserTime0(id, 0);
 }

 static ThreadInfo getThreadInfoForId(long id, int maxDepth)
 {
  Thread[] threads = getAllThreads();
  Thread target = null;
  for (int i = 0; i < threads.length; i++)
  {
   if (threads[i] != null && threads[i].getId() == id)
   {
    target = threads[i];
    break;
   }
  }
  if (target == null)
   return null;
  // For the current thread we can synthesize the trace via a
  // throwaway Throwable -- VMThrowable.fillInStackTrace walks the
  // calling thread. Calling target.getStackTrace() directly would
  // re-enter ManagementFactory.getThreadMXBean().getThreadInfo(...)
  // since classpath's Thread.getStackTrace delegates to the bean,
  // so we never go through that. For arbitrary other threads we
  // suspend the target via captureThreadStackTrace0 (Win32:
  // SuspendThread + GetThreadContext + RtlVirtualUnwind) and feed
  // the captured PCs through VMThrowable.buildStackTrace for the
  // same demangling + line-resolution path as the Throwable case.
  StackTraceElement[] trace;
  if (target == Thread.currentThread())
  {
   trace = new Throwable().getStackTrace();
  }
  else
  {
   Object targetVmdata =
        java.lang.VMAccessorJavaLang.getVmdataVMThread(target);
   if (targetVmdata != null)
   {
    Object captured = captureThreadStackTrace0(targetVmdata);
    if (captured != null)
    {
     trace = java.lang.VMThrowable.buildStackTrace(captured);
    }
    else
    {
     trace = new StackTraceElement[0];
    }
   }
   else
   {
    trace = new StackTraceElement[0];
   }
  }
  if (maxDepth >= 0 && trace.length > maxDepth)
  {
   StackTraceElement[] truncated = new StackTraceElement[maxDepth];
   System.arraycopy(trace, 0, truncated, 0, maxDepth);
   trace = truncated;
  }
  // ThreadInfo has only private constructors. Reflect the 13-arg
  // form so we can populate it from the live thread without
  // patching classpath. setAccessible(true) is JCGO no-op friendly.
  try
  {
   Constructor ctor = ThreadInfo.class.getDeclaredConstructor(
        new Class[] { Long.TYPE, String.class, String.class,
            Long.TYPE, Long.TYPE, String.class,
            Long.TYPE, String.class,
            Long.TYPE, Long.TYPE,
            Boolean.TYPE, Boolean.TYPE,
            StackTraceElement[].class });
   ctor.setAccessible(true);
   Object state = target.getState();
   String stateName;
   try
   {
    java.lang.reflect.Method nameM = state == null ? null
              : state.getClass().getMethod("name", new Class[0]);
    stateName = nameM == null ? "RUNNABLE"
              : (String) nameM.invoke(state, new Object[0]);
   }
   catch (Throwable st)
   {
    stateName = "RUNNABLE";
   }
   return (ThreadInfo) ctor.newInstance(new Object[] {
        Long.valueOf(target.getId()), target.getName(),
        stateName,
        Long.valueOf(0L), Long.valueOf(0L), null,
        Long.valueOf(-1L), null,
        Long.valueOf(0L), Long.valueOf(0L),
        Boolean.FALSE, Boolean.FALSE, trace });
  }
  catch (Throwable t)
  {
   return null;
  }
 }

 static long getThreadUserTime(long id)
 {
  return getThreadCpuUserTime0(id, 1);
 }

 static long getTotalStartedThreadCount()
 {
  return VMAccessorJavaLang.getTotalStartedCountVMThread();
 }

 static void resetPeakThreadCount()
 {
  VMAccessorJavaLang.getPeakThreadCountVMThread(true);
 }

 private static Thread[] getAllThreads()
 {
  ThreadGroup tg = Thread.currentThread().getThreadGroup();
  for (ThreadGroup tgn = tg; tgn != null; tgn = tg.getParent())
   tg = tgn;
  int size = tg.activeCount() + 1;
  Thread[] threads;
  do
  {
   threads = new Thread[size];
   if (tg.enumerate(threads) < size)
    break;
   size += (size >> 1) + 1;
  } while (true);
  return threads;
 }

 private static native long[] findDeadlockedThreads0(
   int isMonitorOnly); /* JVM-core */

 private static native Object getThreadInfoForId0(long id); /* JVM-core */

 private static native long getThreadCpuUserTime0(long id,
   int isUserTime); /* JVM-core */

 // Cross-thread stack-walk. Suspends the target thread, captures
 // its CONTEXT, walks via RtlVirtualUnwind on Win32 x64 (POSIX path
 // uses pthread_kill + sigaction handler — TODO), resumes. Returns
 // a long[] of frame PCs in the same shape VMThrowable.vmdata uses
 // so VMThrowable.buildStackTrace can render it. Returns null when
 // the platform doesn't support cross-thread walking or the
 // suspend/walk failed.
 private static native Object captureThreadStackTrace0(
   Object vmdata); /* JVM-core */
}
