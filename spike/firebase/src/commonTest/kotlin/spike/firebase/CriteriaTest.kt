package spike.firebase

import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.ServerTimestampBehavior
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.details
import dev.gitlive.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.storage.storageMetadata
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Serializable
data class SpikeRequest(val word: String)

@Serializable
data class SpikeReply(val echo: String, val region: String)

/**
 * The criteria of MON-24's Firebase spike, one test per row, in the brief's order. Each runs on the
 * Android emulator and on the iOS simulator against the Auth, Firestore, Functions and Storage
 * emulators, through GitLive's firebase-kotlin-sdk.
 */
class CriteriaTest {

    private val limit = 90.seconds

    /** Every outbox: a write made offline reads back from the cache, then lands on reconnect. */
    @Test
    fun row01_offlineWriteReadsBackThenLandsAfterReconnect() = runSpike(limit) {
        val db = Spike.firestore("row01")
        val ref = db.collection("spike_offline").document(Spike.uniqueId())
        db.disableNetwork()
        // set() completes only when the server acknowledges it, so it is left running.
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            ref.set(mapOf("note" to "written offline"))
        }
        try {
            val cached = ref.get(Source.CACHE)
            assertTrue(cached.exists, "the offline write is in the cache")
            assertEquals("written offline", cached.get<String>("note"))
            assertTrue(cached.metadata.hasPendingWrites, "the cached copy is a pending write")
            assertFalse(pending.isCompleted, "set() has not completed while offline")
        } finally {
            db.enableNetwork()
        }
        withTimeout(30.seconds) { pending.join() }
        // A second app has its own cache, so this read can only come from the server.
        val fromServer = Spike.firestore("row01reader").collection("spike_offline").document(ref.id).get(Source.SERVER)
        assertEquals("written offline", fromServer.get<String>("note"))
    }

    /** Export and request clocks: a SERVER read fails offline and never answers from the cache. */
    @Test
    fun row02_serverReadFailsOfflineWithoutCacheFallback() = runSpike(limit) {
        val db = Spike.firestore("row02")
        val ref = db.collection("spike_source").document(Spike.uniqueId())
        ref.set(mapOf("v" to "server copy"))
        assertEquals("server copy", ref.get(Source.CACHE).get<String>("v"), "the document is cached")
        db.disableNetwork()
        try {
            val error = try {
                ref.get(Source.SERVER)
                null
            } catch (e: FirebaseFirestoreException) {
                e
            }
            assertNotNull(error, "a SERVER read offline must throw, not answer from the cache")
            assertEquals(FirestoreExceptionCode.UNAVAILABLE, error.code)
            // The default source does fall back, which is what makes SERVER the one to name.
            val fallback = ref.get(Source.DEFAULT)
            assertTrue(fallback.metadata.isFromCache)
            assertEquals("server copy", fallback.get<String>("v"))
        } finally {
            db.enableNetwork()
        }
    }

    /** The change-request mirror: a local echo carries hasPendingWrites, the server's copy not. */
    @Test
    fun row03_localEchoHasPendingWrites() = runSpike(limit) {
        val ref = Spike.firestore("main").collection("spike_echo").document(Spike.uniqueId())
        val events = Channel<DocumentSnapshot>(Channel.UNLIMITED)
        val listener = launch {
            ref.snapshots(includeMetadataChanges = true).collect { events.send(it) }
        }
        try {
            events.receive() // the listener is attached: the first answer, before the write
            ref.set(mapOf("status" to "PENDING"))
            var echo = events.receive()
            while (!echo.exists) echo = events.receive()
            assertTrue(echo.metadata.hasPendingWrites, "the first snapshot with the document is the local echo")
            var acked = echo
            while (acked.metadata.hasPendingWrites) acked = events.receive()
            assertTrue(acked.exists)
            assertEquals("PENDING", acked.get<String>("status"))
        } finally {
            listener.cancel()
        }
    }

    /** Request clocks: ESTIMATE reads a pending server timestamp as the local clock's guess. */
    @Test
    fun row04_serverTimestampBehaviorEstimate() = runSpike(limit) {
        val db = Spike.firestore("row04")
        val ref = db.collection("spike_estimate").document(Spike.uniqueId())
        db.disableNetwork()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            ref.set(mapOf("createdAt" to FieldValue.serverTimestamp))
        }
        try {
            val snapshot = ref.get(Source.CACHE)
            assertTrue(snapshot.metadata.hasPendingWrites)
            val none = snapshot.get("createdAt", BaseTimestamp.serializer().nullable, ServerTimestampBehavior.NONE)
            assertNull(none, "NONE reads a pending server timestamp as null")
            val estimate = snapshot.get("createdAt", BaseTimestamp.serializer().nullable, ServerTimestampBehavior.ESTIMATE)
            assertTrue(estimate is Timestamp, "ESTIMATE reads a timestamp, got $estimate")
            assertTrue(abs(estimate.seconds - Timestamp.now().seconds) < 600, "the estimate is the local clock")
        } finally {
            db.enableNetwork()
        }
        withTimeout(30.seconds) { pending.join() }
    }

    /** event_versions: a serverTimestamp() inside set() is stored as the server's time. */
    @Test
    fun row05_serverTimestampInsideSet() = runSpike(limit) {
        val ref = Spike.firestore("main").collection("spike_versions").document(Spike.uniqueId())
        ref.set(
            mapOf(
                "editorUid" to "uid-a",
                "deviceTimeMillis" to 1_700_000_000_000L,
                "recordedAt" to FieldValue.serverTimestamp,
            ),
        )
        val stored = ref.get(Source.SERVER)
        val recordedAt = stored.get("recordedAt", BaseTimestamp.serializer().nullable)
        assertTrue(recordedAt is Timestamp, "recordedAt is a stored timestamp, got $recordedAt")
        assertTrue(abs(recordedAt.seconds - Timestamp.now().seconds) < 600)
        assertEquals("uid-a", stored.get<String>("editorUid"))
        assertEquals(1_700_000_000_000L, stored.get<Long>("deviceTimeMillis"))
    }

    /** Tombstones: update() of two fields leaves every other field of the document alone. */
    @Test
    fun row06_updateOfTwoFieldsLeavesTheRestAlone() = runSpike(limit) {
        val ref = Spike.firestore("main").collection("spike_tombstone").document(Spike.uniqueId())
        ref.set(
            mapOf(
                "title" to "Dentist",
                "createdByFirebaseUid" to "uid-a",
                "sharedWith" to listOf("uid-a", "uid-b"),
                "familyId" to "uid-a_uid-b",
            ),
        )
        ref.update("deletedAtMillis" to 1_700_000_000_000L, "deletedBy" to "uid-b")
        val stored = ref.get(Source.SERVER)
        assertEquals(1_700_000_000_000L, stored.get<Long>("deletedAtMillis"))
        assertEquals("uid-b", stored.get<String>("deletedBy"))
        assertEquals("Dentist", stored.get<String>("title"))
        assertEquals("uid-a", stored.get<String>("createdByFirebaseUid"))
        assertEquals(listOf("uid-a", "uid-b"), stored.get<List<String>>("sharedWith"))
        assertEquals("uid-a_uid-b", stored.get<String>("familyId"))
    }

    /** Custody and split withdrawals: a transaction that reads, then writes what it read. */
    @Test
    fun row07_transactionReadsThenWrites() = runSpike(limit) {
        val db = Spike.firestore("main")
        val ref = db.collection("spike_transaction").document(Spike.uniqueId())
        ref.set(mapOf("proposedAt" to 41L, "proposal" to "week on, week off"))
        val result = db.runTransaction {
            val current = get(ref)
            val proposedAt = current.get<Long>("proposedAt")
            if (proposedAt == 41L) {
                set(ref, mapOf("proposedAt" to proposedAt + 1L, "proposal" to "withdrawn"))
            }
            proposedAt
        }
        assertEquals(41L, result)
        val stored = ref.get(Source.SERVER)
        assertEquals(42L, stored.get<Long>("proposedAt"))
        assertEquals("withdrawn", stored.get<String>("proposal"))
    }

    /** Sync: array-contains and whereIn queries, answered by the server. */
    @Test
    fun row08_arrayContainsAndWhereInQueries() = runSpike(limit) {
        val collection = Spike.firestore("main").collection("spike_query_${Spike.uniqueId()}")
        collection.document("a").set(mapOf("sharedWith" to listOf("uid-a", "uid-b"), "createdBy" to "uid-a"))
        collection.document("b").set(mapOf("sharedWith" to listOf("uid-c"), "createdBy" to "uid-c"))
        collection.document("c").set(mapOf("sharedWith" to listOf("uid-b"), "createdBy" to "uid-b"))
        val sharedWithB = collection.where { "sharedWith" contains "uid-b" }.get(Source.SERVER)
        assertEquals(listOf("a", "c"), sharedWithB.documents.map { it.id }.sorted())
        val byCreators = collection.where { "createdBy" inArray listOf("uid-a", "uid-c") }.get(Source.SERVER)
        assertEquals(listOf("a", "b"), byCreators.documents.map { it.id }.sorted())
    }

    /** The e2e parents: two named apps with their own options, each with its own Firestore. */
    @Test
    fun row09_namedAppsWithTheirOwnOptions() = runSpike(limit) {
        val alice = Spike.app("row09alice")
        val bob = Spike.app("row09bob")
        assertEquals("row09alice", alice.name)
        assertEquals("row09bob", bob.name)
        assertEquals("row09bob", Spike.appByName("row09bob").name)
        val aliceDb = Spike.firestore("row09alice")
        val bobDb = Spike.firestore("row09bob")
        val id = Spike.uniqueId()
        bobDb.disableNetwork()
        try {
            aliceDb.collection("spike_apps").document(id).set(mapOf("by" to "alice"))
            // Bob's instance shares nothing with Alice's: offline, its cache has no such document.
            val bobCached = try {
                bobDb.collection("spike_apps").document(id).get(Source.CACHE)
            } catch (e: FirebaseFirestoreException) {
                null
            }
            assertFalse(bobCached?.exists ?: false, "Bob's cache is his own")
        } finally {
            bobDb.enableNetwork()
        }
        assertEquals("alice", bobDb.collection("spike_apps").document(id).get(Source.SERVER).get<String>("by"))
    }

    /** Pairing and AI: a callable in europe-west3, and the structured details of its refusal. */
    @Test
    fun row10_callableInEuropeWest3WithStructuredErrorDetails() = runSpike(limit) {
        val functions = Spike.functions("main", "europe-west3")
        val reply = functions.httpsCallable("spikeEcho")(SpikeRequest("hello")).data<SpikeReply>()
        assertEquals(SpikeReply(echo = "hello", region = "europe-west3"), reply)

        val refusal = try {
            functions.httpsCallable("spikeEcho")(SpikeRequest("refuse"))
            null
        } catch (e: FirebaseFunctionsException) {
            e
        }
        assertNotNull(refusal, "the callable refuses")
        assertEquals(FunctionsExceptionCode.FAILED_PRECONDITION, refusal.code)
        val details = refusal.details as? Map<*, *>
        assertNotNull(details, "details arrive as a map, got ${refusal.details}")
        assertEquals("spike-refused", details["reason"])
        assertEquals(3L, (details["attempts"] as Number).toLong())

        // The region is part of the address: the same name elsewhere does not exist.
        val elsewhere = try {
            Spike.functions("main", "us-central1").httpsCallable("spikeEcho")(SpikeRequest("hello"))
            null
        } catch (e: FirebaseFunctionsException) {
            e
        }
        assertNotNull(elsewhere, "the callable is not served in us-central1")
    }

    /**
     * MON-23 and L-4: an upload with custom metadata, then a download to a file, and no download
     * URL anywhere. The download uses [downloadToFile], the platform half, because GitLive's common
     * StorageReference has getData and getDownloadUrl but no download to a file.
     */
    @Test
    fun row11_storageUploadWithMetadataDownloadToFileNoUrl() = runSpike(limit) {
        val id = Spike.uniqueId()
        val ref = Spike.storage("main").reference("family_documents/uid-a_uid-b/$id/order.pdf")
        val bytes = "%PDF-1.4 spike $id".encodeToByteArray()
        ref.putFile(
            writeTempFile("upload-$id.pdf", bytes),
            storageMetadata {
                contentType = "application/pdf"
                setCustomMetadata("uploader", "uid-a")
                setCustomMetadata("sha256", "0123abcd")
            },
        )
        val metadata = ref.getMetadata()
        assertNotNull(metadata)
        assertEquals("application/pdf", metadata.contentType)
        assertEquals("uid-a", metadata.customMetadata["uploader"])
        assertEquals("0123abcd", metadata.customMetadata["sha256"])

        val target = tempFilePath("download-$id.pdf")
        downloadToFile(ref, target)
        assertContentEquals(bytes, readFile(target))
    }

    /** Sign-in: a Google ID token credential, against the Auth emulator. */
    @Test
    fun row12_googleIdTokenCredentialOnTheAuthEmulator() = runSpike(limit) {
        val auth = Spike.auth("row12")
        val id = Spike.uniqueId()
        // The Auth emulator takes the claims of a Google ID token as plain JSON. iOS requires an
        // access token beside the ID token (GitLive passes both to FIRGoogleAuthProvider).
        val idToken = """{"sub":"spike-$id","email":"spike-$id@example.com","email_verified":true}"""
        val result = auth.signInWithCredential(GoogleAuthProvider.credential(idToken, "spike-access-token"))
        val user = result.user
        assertNotNull(user)
        assertEquals("spike-$id@example.com", user.email)
        assertTrue(user.providerData.any { it.providerId == "google.com" }, "signed in through google.com")
        assertNotEquals("", user.uid)
        assertEquals(user.uid, auth.currentUser?.uid)
        auth.signOut()
        assertNull(auth.currentUser)
    }

    /**
     * FcmService: the registration token. There is no FCM emulator, and a token needs Google Play
     * services (Android) or APNs (iOS) and a real project, so this checks the API exists and is not
     * a runtime result.
     */
    @Test
    fun row13_fcmRegistrationTokenApiSurfaceOnly() {
        val getToken: suspend FirebaseMessaging.() -> String = { getToken() }
        val deleteToken: suspend FirebaseMessaging.() -> Unit = { deleteToken() }
        assertNotNull(getToken)
        assertNotNull(deleteToken)
    }
}
