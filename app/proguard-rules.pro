# R8 configuration for the release build (`isMinifyEnabled = true`).
#
# This file was empty until the August 2026 security audit, which is how the two
# problems below shipped: neither is reproducible in debug, because debug does not
# minify.

# ---- Crash reports must stay readable ------------------------------------
# Without these, every release stack trace in Crashlytics is a list of obfuscated
# frames with no line numbers, so a production crash cannot be located in the source
# even though the mapping file is uploaded. `-renamesourcefileattribute` keeps the
# original file names out of the shipped binary while the mapping still resolves them.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Stored JSON: no field-name rules since MON-24 ---------------------------
# Until MON-24 the app wrote a child's medications, contacts, school details and medical profile,
# the day swaps (`DayOverride`) and the event draft as Gson JSON, and Gson derived every key from
# the Kotlin *field name* by reflection. R8 renames fields it cannot see being read, so this file
# kept the fields of `com.coparently.app.domain.model.**`, `EventDraft`, `DayOverride` and
# `DayOverrideStatus` by name: left unkept, a release build once wrote `{"a":"penicillin"}`, the
# co-parent's phone read an empty medical profile, and a row written by one version did not parse
# in the next.
#
# `StoredJson` (`:shared`) replaced Gson. Its serializers are generated at compile time and carry
# each key as a string literal, so R8 renaming a field changes nothing that is written, and no
# rule for the models is needed; the library's own consumer rules cover the generated serializers.
# The R8 runtime probe (`app/src/r8Test/`, `R8JsonProbe`) still writes every stored model through
# the minified app and checks its keys, which is what would catch a regression.
#
# Kept from the Gson section: generic signatures and runtime annotations, which libraries read
# reflectively (Google's calendar client read both, and brought its own Gson, until it left in
# MON-24 phase 2), and the warning about `sun.misc`, which Gson's `UnsafeAllocator` references.
# Removing them is a change of its own, made only with an `r8-runtime` run to show nothing else
# relied on them.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ---- Strip verbose logging from the release binary -----------------------
# `Log.d`/`v`/`i` calls in this codebase carry family data: calendar contents, chat text,
# sign-in emails, sync payloads. In a release build
# those land in logcat, where any app holding READ_LOGS on a rooted or developer
# device — and any bug-report capture — can read them. The call sites that logged
# personal data outright were removed in the same audit; this is the backstop that
# keeps a future `Log.d("...", "$message")` out of a shipped APK.
#
# `Log.w` and `Log.e` are deliberately kept: they are what makes a production crash
# report legible, and they are reviewed not to carry content — nor, since the September 2026
# audit, account identifiers: uids and family ids (which embed two uids) were taken out of
# every warning and error message.
# Requires the optimizing configuration, which `proguard-android-optimize.txt`
# (referenced from `build.gradle.kts`) supplies.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ---- SQLCipher (SEC-2) ---------------------------------------------------
# The database is opened through `net.zetetic.database.sqlcipher`, whose native layer
# calls back into Java by name: R8 renaming one of those members produces a
# `NoSuchMethodError` at the first query rather than a build error, in a release
# build only, before Room's first statement runs — a launch crash, not a degradation.
#
# This is the first rule of the `proguard.txt` the AAR itself ships, copied verbatim
# (AGP extracts and applies that file, so this is a duplicate rather than a fix). It is
# repeated here on purpose: if that extraction ever silently stops happening, the
# failure is a crash on a shipped build, and the cost of the duplicate is nothing. The
# two `-keepclassmembers` rules the AAR also carries are left to it — they cover
# `SQLiteCustomFunction` and `SQLiteDebug$PagerStats`, neither of which this app
# reaches. Deliberately not a blanket `{ *; }` keep of the package: that would pin
# megabytes of unreachable code and hide which members actually have to survive.
-keep class net.zetetic.** {
    native <methods>;
    private native <methods>;
    public <init>(...);
    long mNativeHandle;
}
-dontwarn net.zetetic.database.**

# ---- Ktor (MON-24 phase 2) -------------------------------------------------
# The school import and the Google Calendar import call out through Ktor (`:shared-data`), whose
# own consumer rules keep what it needs. Its JVM utilities reference `java.lang.management`, which
# Android does not have, to ask whether a debugger is attached; R8 would fail the build on the
# missing classes. Nothing on Android reaches that code.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# The Google Calendar API models' rules (REL-7) left with google-api-client in MON-24 phase 2:
# `-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault`, a
# `-keepclassmembers` rule for `@com.google.api.client.util.Key` fields, a `-keep` of every
# `GenericJson` subclass's constructor and fields, and `-keep class
# com.google.api.services.calendar.model.** { *; }`. The import now reads a page by hand over a
# JSON tree (`GoogleCalendarJson`), which reflects over nothing.
