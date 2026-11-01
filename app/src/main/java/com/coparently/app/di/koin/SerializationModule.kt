package com.coparently.app.di.koin

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import org.koin.dsl.module

/**
 * The one injectable [Gson], as the graph builds it — a function of its own so that the R8 probe
 * (`R8GsonProbe`) builds exactly this instance without starting the graph.
 *
 * It lived in `AIModule` until the AI subsystem was deleted (MON-7), which is a poor place for a
 * general serialization binding and cost a CI round to discover: nothing in the AI packages was
 * referenced from outside them, but the module in that package was providing something that was.
 * A module named after a feature should only provide that feature's bindings.
 *
 * Lenient parsing (`setLenient()`, now spelled `setStrictness(Strictness.LENIENT)` — Gson 2.11
 * deprecated the old name, the behaviour is identical) is preserved from that provider rather
 * than reconsidered. It changes how malformed JSON is read, and the only consumer —
 * [com.coparently.app.presentation.event.EventViewModel] — parses values that have been through
 * Room and Firestore; tightening it is a behaviour change that belongs in its own commit, with
 * whatever it breaks in front of the person making it.
 *
 * Note that most classes here do **not** inject Gson: repositories construct their own
 * `Gson()` (a strict one, and in `ChildInfoRepositoryImpl` one with a `LocalDate` adapter
 * registered). That inconsistency is pre-existing and is not resolved by this module — it is
 * simply where the injectable instance now lives.
 */
fun provideGson(): Gson = GsonBuilder()
    .setStrictness(Strictness.LENIENT)
    .create()

/** Provides [provideGson]'s instance, once for the process. */
val serializationModule = module {
    single { provideGson() }
}
