package com.coparently.app.r8probe

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Bundle

/**
 * The entry point of the R8 runtime probe: `am instrument -w -r
 * app.coplanly/com.coparently.app.r8probe.R8ProbeInstrumentation` (see `tools/run-r8-probe.sh`).
 *
 * It instruments its own package, so it runs inside the minified app process, over the same dex
 * the rest of the app was shrunk into. Each [ProbeCase] is reported as one
 * `INSTRUMENTATION_STATUS` block, and the run ends with `INSTRUMENTATION_RESULT: probe=done`;
 * `tools/check-r8-probe.js` reads both. A process that dies before that line has not passed.
 *
 * Kept by `app/proguard-r8test.pro`, which only the `r8Test` build type reads.
 */
class R8ProbeInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    /**
     * Deliberately does **not** call `Application.onCreate`.
     *
     * `CoPlanlyApplication.onCreate` injects its Hilt graph, and that graph builds Firebase's
     * default-app singletons (`FirebaseAuth.getInstance()`), which throw without a
     * `google-services.json` — CI has none, and must not get one (CLAUDE.md). The probe builds
     * the classes it needs by hand, as the e2e parents do, so the Application object is attached
     * and never started: nothing of the app runs but what the probe calls.
     */
    override fun callApplicationOnCreate(app: Application?) = Unit

    override fun onStart() {
        super.onStart()
        val cases = R8GsonProbe(targetContext).run()
        for (case in cases) {
            sendStatus(
                STATUS_CASE,
                Bundle().apply {
                    putString("type", case.type)
                    putString("via", case.via)
                    putString("ok", case.ok.toString())
                    putString("detail", case.detail)
                    putString("json", case.json)
                }
            )
        }
        finish(
            Activity.RESULT_OK,
            Bundle().apply {
                putString("probe", "done")
                putString("cases", cases.size.toString())
                putString("failed", cases.count { !it.ok }.toString())
            }
        )
    }

    private companion object {
        /** Any status code; the check script keys on the block, not on this number. */
        const val STATUS_CASE = 1
    }
}
