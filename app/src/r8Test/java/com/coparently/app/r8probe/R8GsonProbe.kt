package com.coparently.app.r8probe

import android.content.Context
import androidx.room.Room
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.Converters
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.remote.firebase.FirestorePetDataSource
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.DayOverrideJson
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.toDomain
import com.coparently.app.data.repository.toEntity
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.di.SerializationModule
import com.coparently.app.presentation.event.EventDraft
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.model.Events
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Round-trips every type the app hands to Gson through the app's **own** serialization code, in a
 * minified build, and reports what came out (REL-7).
 *
 * `tools/check-r8-mapping.js` proves the keep rules held for field *names*; this proves the
 * minified code *runs*: that the JSON keys are the source names, that a `TypeToken` still carries
 * its generic signature (R8 full mode strips `Signature` from classes nothing keeps, and Gson then
 * throws), that Gson can still construct each model, and that what is written reads back equal.
 * It is compiled only into the `r8Test` build type, which is `release` plus this package, a keep
 * rule for its entry point (`app/proguard-r8test.pro`) and the debug signing key.
 *
 * **Production paths, not copies of them.** A child and a pet go through `ChildInfoRepositoryImpl`
 * and `PetRepositoryImpl` into a real (in-memory) Room database, and the probe reads the stored
 * row's JSON columns and then the repository's own read-back. Custody swaps go through
 * `DayOverrideJson`, the event draft through the injectable Gson `SerializationModule` provides,
 * chat and revisions through their mappers — each with the `TypeToken` its file declares. For a
 * `X::class.java` target the call site is immaterial (renaming is a property of the class); for a
 * `TypeToken` it is the anonymous class at the call site that R8 may strip, so those are always
 * the production ones.
 *
 * **Signed out, on a Firebase app of its own.** The repositories write to Firestore only for a
 * signed-in user, and this one never signs in: a named [FirebaseApp] on the credential-free
 * `demo-coplanly` project, as the e2e parents use, reaches no server and needs no
 * `google-services.json`. So the Firestore map `ChildInfoRepositoryImpl.toFirestoreMap()` builds
 * is not itself inspected — but its `medicalProfile` is `gson.fromJson(gson.toJson(profile),
 * Map::class.java)` on the same Gson instance that wrote the Room column checked here, so the keys
 * are the same keys; and it reads a downloaded one back through the same `fromJson` as the row.
 *
 * The one thing this build cannot be is byte-identical to `release`: the probe is extra code, so
 * R8 sees extra callers and may inline differently. What it decides by *rule* — which fields keep
 * their names, which classes keep their signatures — is the same, and that is what is checked.
 */
internal class R8GsonProbe(private val context: Context) {

    /** Runs every case; never throws — a failure to even start is reported as a case too. */
    fun run(): List<ProbeCase> {
        var cases: List<ProbeCase> = emptyList()
        val setup = guarded(listOf(PROBE), "setting up Room and Firebase") {
            val firebase = firebaseApp()
            val auth = FirebaseAuth.getInstance(firebase).also { it.signOut() }
            val firestore = FirebaseFirestore.getInstance(firebase)
            val database = Room.inMemoryDatabaseBuilder(context, CoPlanlyDatabase::class.java).build()
            try {
                cases = runBlocking {
                    childCases(database, FirebaseAuthService(auth), firestore) +
                        petCases(database, FirebaseAuthService(auth), firestore)
                }
            } finally {
                database.close()
            }
        }
        return setup + cases + dayOverrideCases() + eventDraftCases() + calendarCases() + typeTokenCases()
    }

    private fun firebaseApp(): FirebaseApp =
        FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("demo-coplanly")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    // Not a key — the same placeholder the e2e parents use, shaped as the SDK
                    // requires and kept off the `AIza` prefix so secret scanning ignores it.
                    .setApiKey("A-fake-key-for-the-firebase-emulators-x")
                    .build(),
                FIREBASE_APP_NAME
            )

    private suspend fun childCases(
        database: CoPlanlyDatabase,
        auth: FirebaseAuthService,
        firestore: FirebaseFirestore
    ): List<ProbeCase> {
        val repository = ChildInfoRepositoryImpl(
            childInfoDao = database.childInfoDao(),
            userDao = database.userDao(),
            firebaseAuthService = auth,
            firestoreChildInfoDataSource = FirestoreChildInfoDataSource(firestore)
        )
        val via = "ChildInfoRepositoryImpl.upsertChildInfo -> Room child_info -> getChildInfoById"
        return guarded(CHILD_TYPES, via) {
            val child = ProbeFixtures.child
            repository.upsertChildInfo(child)
            val row = checkNotNull(database.childInfoDao().getChildInfoById(child.id)) { "no child_info row" }
            val back = checkNotNull(repository.getChildInfoById(child.id)) { "the row did not read back" }
            val profile = JSONObject(row.medicalProfileJson)
            val vaccinations = profile.getJSONArray("vaccinations")

            keys(MEDICATION, row.medicationsJson, MEDICATION_KEYS, back.medications == child.medications)
            keys(ACTIVITY, row.activitiesJson, ACTIVITY_KEYS, back.activities == child.activities)
            keys(
                EMERGENCY_CONTACT,
                row.emergencyContactsJson,
                EMERGENCY_CONTACT_KEYS,
                back.emergencyContacts == child.emergencyContacts
            )
            keys(SCHOOL_INFO, row.schoolInfoJson.orEmpty(), SCHOOL_INFO_KEYS, back.schoolInfo == child.schoolInfo)
            keys(
                MEDICAL_PROFILE,
                row.medicalProfileJson,
                MEDICAL_PROFILE_KEYS,
                back.medicalProfile == child.medicalProfile
            )
            value(BLOOD_TYPE, row.medicalProfileJson, "A_POSITIVE", profile.optString("bloodType"))
            keys(
                VACCINATION,
                vaccinations.toString(),
                VACCINATION_KEYS,
                back.medicalProfile.vaccinations == child.medicalProfile.vaccinations
            )
            // `LocalDateJsonAdapter`: the date is written as ISO text, not as a reflected object.
            value(
                VACCINATION,
                vaccinations.toString(),
                ProbeFixtures.VACCINATION_DATE,
                vaccinations.getJSONObject(0).optString("date")
            )
            roundTrip(CHILD_INFO, row.medicalProfileJson, back == child)
        }
    }

    private suspend fun petCases(
        database: CoPlanlyDatabase,
        auth: FirebaseAuthService,
        firestore: FirebaseFirestore
    ): List<ProbeCase> {
        val repository = PetRepositoryImpl(
            petDao = database.petDao(),
            userDao = database.userDao(),
            firebaseAuthService = auth,
            firestorePetDataSource = FirestorePetDataSource(firestore)
        )
        val via = "PetRepositoryImpl.upsertPet -> Room pets -> getPetById"
        return guarded(listOf(MEDICATION, VACCINATION, PET), via) {
            val pet = ProbeFixtures.pet
            repository.upsertPet(pet)
            val row = checkNotNull(database.petDao().getPetById(pet.id)) { "no pets row" }
            val back = checkNotNull(repository.getPetById(pet.id)) { "the row did not read back" }
            keys(MEDICATION, row.medicationsJson, MEDICATION_KEYS, back.medications == pet.medications)
            keys(VACCINATION, row.vaccinationsJson, VACCINATION_KEYS, back.vaccinations == pet.vaccinations)
            roundTrip(PET, row.vaccinationsJson, back == pet)
        }
    }

    private fun dayOverrideCases(): List<ProbeCase> {
        val via = "DayOverrideJson (TypeToken<Map<String, DayOverride>>)"
        return guarded(listOf(DAY_OVERRIDE, DAY_OVERRIDE_STATUS), via) {
            val overrides = mapOf(ProbeFixtures.DAY_OVERRIDE_DATE to ProbeFixtures.dayOverride)
            val json = checkNotNull(DayOverrideJson.encode(overrides)) { "encode returned null for one swap" }
            val stored = JSONObject(json).getJSONObject(ProbeFixtures.DAY_OVERRIDE_DATE).toString()
            // `decode` degrades to an empty map on any failure, by design — so "equal" is the check
            // that it did not quietly drop every swap, the release-only symptom this guards.
            keys(DAY_OVERRIDE, stored, DAY_OVERRIDE_KEYS, DayOverrideJson.decode(json) == overrides)
            value(DAY_OVERRIDE_STATUS, stored, "ACCEPTED", JSONObject(stored).optString("status"))
        }
    }

    private fun eventDraftCases(): List<ProbeCase> {
        val via = "SerializationModule.provideGson (lenient), as EventViewModel saves a draft"
        return guarded(listOf(EVENT_DRAFT), via) {
            val gson = SerializationModule.provideGson()
            val json = gson.toJson(ProbeFixtures.eventDraft)
            val back = gson.fromJson(json, EventDraft::class.java)
            keys(EVENT_DRAFT, json, EVENT_DRAFT_KEYS, back == ProbeFixtures.eventDraft)
        }
    }

    private fun calendarCases(): List<ProbeCase> {
        val via = "GsonFactory and the @Key models, as GoogleCalendarApi reads a page"
        return guarded(listOf(CALENDAR_EVENTS), via) {
            val factory = GsonFactory.getDefaultInstance()
            val page = factory.fromString(ProbeFixtures.calendarPage, Events::class.java)
            val event = checkNotNull(page.items?.singleOrNull()) { "the page parsed with no items" }
            val parsed = event.summary == "Swimming" &&
                event.start?.dateTime?.toStringRfc3339() == ProbeFixtures.CALENDAR_START
            keys(CALENDAR_EVENTS, factory.toString(event), CALENDAR_EVENT_KEYS, parsed)
        }
    }

    /**
     * The `TypeToken`s over library types. Nothing here can be renamed, but each anonymous
     * `TypeToken` subclass needs its generic signature at runtime, and full-mode R8 keeps a
     * signature only where a rule says so (Gson 2.11's own consumer rules do, today).
     */
    private fun typeTokenCases(): List<ProbeCase> {
        val converters = guarded(listOf(TOKEN_CONVERTERS), "Converters (Room TypeConverter)") {
            val tool = Converters()
            val list = listOf("uid-a", "uid-b")
            val json = tool.fromStringList(list)
            roundTrip(TOKEN_CONVERTERS, json, tool.toStringList(json) == list)
        }
        val chat = guarded(listOf(TOKEN_CHAT), "ChatMappers toEntity/toDomain") {
            val conversation = ProbeFixtures.conversation.toEntity()
            val message = ProbeFixtures.message.toEntity()
            roundTrip(TOKEN_CHAT, conversation.lastReadAtJson, conversation.toDomain() == ProbeFixtures.conversation)
            roundTrip(TOKEN_CHAT, message.attachmentsJson, message.toDomain() == ProbeFixtures.message)
        }
        val versions = guarded(listOf(TOKEN_EVENT_VERSIONS), "EventVersionDocument encode/decode") {
            val snapshot = EventVersionDocument.encodeSnapshot(ProbeFixtures.eventSnapshot)
            val audience = listOf("uid-a", "uid-b")
            val audienceJson = EventVersionDocument.encodeAudience(audience)
            val snapshotBack = EventVersionDocument.decodeSnapshot(snapshot)
            roundTrip(TOKEN_EVENT_VERSIONS, snapshot, snapshotBack == ProbeFixtures.eventSnapshot)
            roundTrip(TOKEN_EVENT_VERSIONS, audienceJson, EventVersionDocument.decodeAudience(audienceJson) == audience)
        }
        return converters + chat + versions
    }

    /** The names the report uses. Literals on purpose — see [ProbeCase.type]. */
    companion object {
        private const val FIREBASE_APP_NAME = "r8-probe"

        /** The label a failure to set the probe up at all is reported under. */
        const val PROBE = "probe"

        // Every type tools/check-invariants.js finds handed to Gson must be named in this
        // directory as a literal, and tools/check-r8-probe.js requires a passing case for each.
        const val MEDICATION = "com.coparently.app.domain.model.Medication"
        const val ACTIVITY = "com.coparently.app.domain.model.Activity"
        const val EMERGENCY_CONTACT = "com.coparently.app.domain.model.EmergencyContact"
        const val SCHOOL_INFO = "com.coparently.app.domain.model.SchoolInfo"
        const val MEDICAL_PROFILE = "com.coparently.app.domain.model.MedicalProfile"
        const val BLOOD_TYPE = "com.coparently.app.domain.model.BloodType"
        const val VACCINATION = "com.coparently.app.domain.model.Vaccination"
        const val DAY_OVERRIDE = "com.coparently.app.domain.custody.DayOverride"
        const val DAY_OVERRIDE_STATUS = "com.coparently.app.domain.custody.DayOverrideStatus"
        const val EVENT_DRAFT = "com.coparently.app.presentation.event.EventDraft"

        // Whole records, a library model and library-type tokens: checked, not discovered.
        const val CHILD_INFO = "com.coparently.app.domain.model.ChildInfo"
        const val PET = "com.coparently.app.domain.model.Pet"
        const val CALENDAR_EVENTS = "com.google.api.services.calendar.model.Events"
        const val TOKEN_CONVERTERS = "TypeToken<List<String>> in Converters"
        const val TOKEN_CHAT = "TypeTokens in ChatMappers"
        const val TOKEN_EVENT_VERSIONS = "TypeTokens in EventVersionDocument"

        private val CHILD_TYPES = listOf(
            MEDICATION,
            ACTIVITY,
            EMERGENCY_CONTACT,
            SCHOOL_INFO,
            MEDICAL_PROFILE,
            BLOOD_TYPE,
            VACCINATION,
            CHILD_INFO
        )

        private val MEDICATION_KEYS = setOf("name", "dosage", "frequency", "notes")
        private val ACTIVITY_KEYS = setOf("name", "schedule", "location", "contactPerson", "contactPhone")
        private val EMERGENCY_CONTACT_KEYS = setOf("name", "relationship", "phone", "alternatePhone")
        private val SCHOOL_INFO_KEYS = setOf("name", "address", "phone", "teacherName", "teacherEmail", "grade")
        private val MEDICAL_PROFILE_KEYS = setOf("bloodType", "intolerances", "hereditaryConditions", "vaccinations")
        private val VACCINATION_KEYS = setOf("name", "date")
        private val DAY_OVERRIDE_KEYS = setOf(
            "toParent",
            "requestedBy",
            "requestedAt",
            "status",
            "decidedBy",
            "decidedAt",
            "note",
            "groupId"
        )
        private val EVENT_DRAFT_KEYS = setOf(
            "title",
            "description",
            "parentOwner",
            "eventType",
            "startDate",
            "startTime",
            "endTime"
        )
        private val CALENDAR_EVENT_KEYS = setOf("id", "summary", "start", "end")
    }
}
