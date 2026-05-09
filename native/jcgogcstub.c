/*
 * @(#) $(JCGO)/native/jcgogcstub.c --
 * a part of the JCGO runtime subsystem.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

/*
 * Linker-satisfying stubs for the BDWGC entry points JCGO's emitted
 * C calls. None of these run a real garbage collector — `GC_init`
 * is a no-op, allocations come from `malloc`, finalization and root
 * registration silently succeed. This is sufficient for STRUCTURAL
 * builds where you want the linker / debugger / disassembler to be
 * happy but you don't need the binary to actually run a workload —
 * the canonical user is `mkjcgo/test-e2e.sh`'s MSVC PDB-inspection
 * step, which links a verify_msvc.exe purely to inspect its PDB for
 * #line-derived source filenames.
 *
 * Real builds (mingw, MSVC release) link against a proper BDWGC
 * library (`libs/<arch>/<toolchain>/libgc.{a,lib}`) and don't pull
 * this file in. This stub is only included when the caller opts in
 * by adding `native/jcgogcstub.c` to the compile inputs and skipping
 * the real libgc.
 *
 * If you find yourself wondering why a binary with this stub
 * crashes at runtime: that's by design. It exists to satisfy the
 * linker, not to substitute for a real GC.
 */

#include <stdlib.h>
#include <string.h>

#if defined(_MSC_VER)
#define JCGO_GC_API __declspec(dllexport)
#else
#define JCGO_GC_API
#endif

typedef void (*JCGO_GC_warn_proc)(char *msg, unsigned long arg);
typedef void (*JCGO_GC_finalization_proc)(void *obj, void *cd);

JCGO_GC_API void GC_init(void) { }
JCGO_GC_API void GC_init_gcj_malloc(int mp_index, void *mp) {
    (void)mp_index; (void)mp;
}

JCGO_GC_API void *GC_malloc(size_t n) {
    void *p = malloc(n ? n : 1);
    if (p != NULL) memset(p, 0, n);
    return p;
}
JCGO_GC_API void *GC_malloc_atomic(size_t n) {
    void *p = malloc(n ? n : 1);
    if (p != NULL) memset(p, 0, n);
    return p;
}
JCGO_GC_API void *GC_gcj_malloc(size_t n, void *vt) {
    void *p = malloc(n ? n : 1);
    if (p != NULL) {
        memset(p, 0, n);
        if (vt != NULL && n >= sizeof(void *)) {
            *(void **)p = vt;
        }
    }
    return p;
}
JCGO_GC_API void *GC_base(void *p) { return p; }

JCGO_GC_API void GC_gcollect(void) { }
JCGO_GC_API void GC_gcollect_and_unmap(void) { }
JCGO_GC_API unsigned long GC_get_gc_no(void) { return 0; }
JCGO_GC_API size_t GC_get_heap_size(void) { return 0; }
JCGO_GC_API size_t GC_get_free_bytes(void) { return 0; }
JCGO_GC_API int GC_expand_hp(size_t n) { (void)n; return 0; }

JCGO_GC_API void GC_set_all_interior_pointers(int v) { (void)v; }
JCGO_GC_API void GC_set_finalize_on_demand(int v) { (void)v; }
JCGO_GC_API void GC_set_java_finalization(int v) { (void)v; }

JCGO_GC_API void GC_register_finalizer_no_order(void *obj,
        JCGO_GC_finalization_proc fn, void *cd,
        JCGO_GC_finalization_proc *ofn, void **ocd) {
    (void)obj; (void)fn; (void)cd;
    if (ofn != NULL) *ofn = NULL;
    if (ocd != NULL) *ocd = NULL;
}
JCGO_GC_API int GC_general_register_disappearing_link(void **link,
        const void *obj) {
    (void)link; (void)obj;
    return 0;
}
JCGO_GC_API int GC_invoke_finalizers(void) { return 0; }

JCGO_GC_API void GC_add_roots(void *low, void *high) {
    (void)low; (void)high;
}

JCGO_GC_API void GC_set_warn_proc(JCGO_GC_warn_proc p) { (void)p; }
JCGO_GC_API void GC_ignore_warn_proc(char *msg, unsigned long arg) {
    (void)msg; (void)arg;
}
