/*
 * @(#) $(JCGO)/include/jcgocrash.h --
 * Public API for the JCGO crash-dump handler.
 *
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

/*
 * Lets an application register a callback that runs after the
 * runtime's built-in crash handler has finished writing its .stk
 * file. Typical use: ship the crash report somewhere (a log server,
 * a bug tracker, the user's clipboard, ...) without having to
 * replace the built-in handling.
 *
 * The shape of this API is deliberately platform-neutral. The
 * meaning of `code` and the contents of `code_name` are
 * implementation-defined (Windows: ExceptionCode + symbolic name;
 * Unix: signal number + signal name); everything else is the same
 * shape on every platform.
 */

#ifndef JCGO_CRASH_H
#define JCGO_CRASH_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * One node per stack frame, threaded by `next` into the linked list
 * rooted at jcgo_crash_info_t::frames. Frame ordering is innermost
 * first (faulting frame at the head). `repeat_count` collapses runs
 * of identical consecutive PCs: a value of N means "this frame, then
 * N additional identical frames"; 0 means no repeats.
 *
 * The strings (`module`, `symbol`, `file`) are NUL-terminated and
 * may be the empty string if the symbolizer could not resolve them.
 * `line` is 0 when source-line info is unavailable.
 */
typedef struct jcgo_crash_frame_s {
    struct jcgo_crash_frame_s *next;
    unsigned long long  pc;
    unsigned long long  offset_from_base;   /* pc - module_base */
    unsigned long long  sym_displacement;
    unsigned long       line;
    int                 repeat_count;
    char                module[64];
    char                symbol[256];
    char                file[256];
} jcgo_crash_frame_t;

/*
 * Crash summary handed to the user callback.
 *
 * Lifetime: `info`, its strings, and the entire `frames` list are
 * owned by the runtime and valid only for the duration of the
 * callback. If the callback wants the data later (e.g. to ship it
 * from a worker thread), it must copy.
 */
typedef struct jcgo_crash_info_s {
    unsigned long       code;          /* platform-specific numeric code */
    const char         *code_name;     /* "ACCESS_VIOLATION", "SIGSEGV", ... */
    void               *fault_addr;
    unsigned long long  module_base;
    const char         *stk_path;      /* file the runtime wrote; "" if write failed */
    jcgo_crash_frame_t *frames;        /* head of frame list, NULL if empty */
    int                 frame_count;
} jcgo_crash_info_t;

/*
 * Callback prototype. Runs in a half-dead process: the heap may be
 * corrupt, GC is gone, other threads are in unknown states. Must not
 * throw, must not block indefinitely, must return promptly. `vpcb`
 * is whatever opaque pointer was passed to jcgo_crash_set_callback.
 */
typedef void (*jcgo_crash_callback_t)(const jcgo_crash_info_t *info,
                                      void *vpcb);

/*
 * Install (or, with cb == NULL, clear) the post-dump callback. The
 * most recent call wins; only one callback is held at a time. Not
 * synchronized against concurrent registration -- intended to be
 * called once during application startup.
 */
void jcgo_crash_set_callback(jcgo_crash_callback_t cb, void *vpcb);

#ifdef __cplusplus
}
#endif

#endif /* JCGO_CRASH_H */
