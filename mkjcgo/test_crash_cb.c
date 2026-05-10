/*
 * Tiny self-contained test for jcgo_crash_set_callback.
 *
 * Registers a callback that writes the crash info it receives into a
 * sentinel file, then triggers a deliberate null dereference. The
 * companion test script (test-crash-cb.sh) runs the resulting
 * binary, expects it to die, and asserts the sentinel was written
 * with the right shape.
 */

#include <stdio.h>
#include <stdlib.h>

#include "jcgocrash.h"

#define SENTINEL_PATH "test_crash_cb_sentinel.txt"

static int counter_for_vpcb = 0;

static void my_cb(const jcgo_crash_info_t *info, void *vpcb)
{
    int *counter = (int *)vpcb;
    FILE *f = fopen(SENTINEL_PATH, "w");
    if (counter) (*counter)++;
    if (!f) return;
    fprintf(f, "code=0x%08lX\n", info->code);
    fprintf(f, "code_name=%s\n",
            info->code_name ? info->code_name : "(null)");
    fprintf(f, "frame_count=%d\n", info->frame_count);
    fprintf(f, "vpcb_counter=%d\n", counter ? *counter : -1);
    fprintf(f, "stk_path=%s\n", info->stk_path ? info->stk_path : "(null)");
    if (info->frames) {
        fprintf(f, "first_frame_symbol=%s\n", info->frames->symbol);
        fprintf(f, "first_frame_module=%s\n", info->frames->module);
    }
    fclose(f);
}

int main(void)
{
    volatile int *bad;
    remove(SENTINEL_PATH);
    jcgo_crash_set_callback(my_cb, &counter_for_vpcb);
    bad = (volatile int *)0;
    return *bad;  /* boom */
}
