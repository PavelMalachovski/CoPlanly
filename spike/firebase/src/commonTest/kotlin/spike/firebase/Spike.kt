package spike.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.app
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.persistentCacheSettings
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage
import kotlin.random.Random

/** The named apps and their emulator-bound services, each set up once per process. */
object Spike {
    const val PROJECT_ID = "demo-coplanly"

    private val firestores = mutableMapOf<String, FirebaseFirestore>()
    private val auths = mutableMapOf<String, FirebaseAuth>()
    private val functions = mutableMapOf<String, FirebaseFunctions>()
    private val storages = mutableMapOf<String, FirebaseStorage>()

    private fun options() = FirebaseOptions(
        applicationId = "1:123456789000:$appIdPlatform:0123456789abcdef",
        apiKey = "AIzaSpikeKeyForTheEmulatorsOnly00000000",
        databaseUrl = "https://$PROJECT_ID.firebaseio.com",
        storageBucket = "$PROJECT_ID.appspot.com",
        projectId = PROJECT_ID,
        gcmSenderId = "123456789000",
    )

    /** A named app with its own options, created on first use. */
    fun app(name: String): FirebaseApp =
        Firebase.apps(platformContext).firstOrNull { it.name == name }
            ?: Firebase.initialize(platformContext, options(), name)

    /** The app's Firestore, with the persistent cache, on the emulator. */
    fun firestore(name: String): FirebaseFirestore = firestores.getOrPut(name) {
        Firebase.firestore(app(name)).apply {
            settings = firestoreSettings {
                cacheSettings = persistentCacheSettings { }
            }
            useEmulator(emulatorHost, 8080)
        }
    }

    fun auth(name: String): FirebaseAuth = auths.getOrPut(name) {
        Firebase.auth(app(name)).apply { useEmulator(emulatorHost, 9099) }
    }

    fun functions(name: String, region: String): FirebaseFunctions = functions.getOrPut("$name/$region") {
        Firebase.functions(app(name), region).apply { useEmulator(emulatorHost, 5001) }
    }

    fun storage(name: String): FirebaseStorage = storages.getOrPut(name) {
        Firebase.storage(app(name)).apply { useEmulator(emulatorHost, 9199) }
    }

    /** The default app's name never leaks into a test: every row names its own. */
    fun appByName(name: String): FirebaseApp = Firebase.app(name)

    fun uniqueId(): String = Random.nextLong().toULong().toString(16)
}
