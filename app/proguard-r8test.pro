# R8 rules for the `r8Test` build type only (`app/build.gradle.kts`), read *in addition to*
# everything `release` reads. Never add a rule for app code here: a keep that only this build has
# would make the probe pass on a class the shipped build still breaks.
#
# The one entry point of the R8 runtime probe (REL-7, `app/src/r8Test/`). The manifest names it,
# and aapt2's generated rules should keep it for that alone; this says so explicitly rather than
# depending on it. Only the class and its constructor: the methods the framework calls are
# overrides of `android.app.Instrumentation` and survive as such, and everything the probe calls
# — the repositories, the mappers, the models — is shrunk, optimised and renamed exactly as in
# `release`, which is the point.
-keep class com.coparently.app.r8probe.R8ProbeInstrumentation {
    <init>();
}
