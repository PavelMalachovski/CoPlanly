package com.coparently.app.testing

/**
 * Marks an instrumented test that loads the fake Firebase override (`fakeFirebaseModule`, through
 * [KoinTestRule]).
 *
 * That override mocks Firebase through MockK's inline-mocking agent, `libmockkjvmtiagent.so`,
 * which does not dlopen on 16 KB memory pages. The 16 KB emulator leg therefore skips every class
 * carrying this annotation (`notAnnotation=com.coparently.app.testing.UsesFakeFirebase` in
 * `tools/ci-changes.js`), which is a test-tool failure that says nothing about the app. Put it on
 * every concrete test class that uses [KoinTestRule], and on the abstract base classes too, so a
 * subclass written from one of them is not missed.
 */
@Target(AnnotationTarget.CLASS)
annotation class UsesFakeFirebase
