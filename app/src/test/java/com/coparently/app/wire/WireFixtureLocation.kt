package com.coparently.app.wire

import java.io.File

/**
 * Where this module's run of the wire harness finds the fixtures, and whether to rewrite
 * `wire/current/`. `:shared-data` generates its own from its build; here, Gradle runs unit tests in
 * the module directory and an IDE may use the root, as before the harness moved.
 */
internal object WireFixtureLocation {

    /** `app/src/test/resources/wire`. */
    val ROOT: String by lazy {
        listOf(File("src/test/resources/wire"), File("app/src/test/resources/wire"))
            .firstOrNull { it.isDirectory }
            ?.path
            ?: error("No wire fixture directory under ${File("").absolutePath}")
    }

    /** Whether `UPDATE_WIRE_FIXTURES` asks `CurrentWireFixturesTest` to rewrite `wire/current/`. */
    val UPDATE: Boolean = System.getenv(WireFixtures.UPDATE_ENV).orEmpty().let { it == "1" || it == "true" }
}
