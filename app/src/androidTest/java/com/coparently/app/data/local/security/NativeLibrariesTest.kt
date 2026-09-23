package com.coparently.app.data.local.security

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.local.CoPlanlyDatabase
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Loads every native library the app ships and makes each one do real work — the check that a
 * **16 KB page** device (Play's requirement, the API 35 `google_apis_ps16k` leg) can `dlopen`
 * them at all. A library whose ELF segments are 4 KB-aligned fails there before any Kotlin runs.
 *
 * Deliberately **not** a Hilt test and **no MockK**: MockK's own inline-mocking agent
 * (`libmockkjvmtiagent.so`) is a test-only library that does not load on 16 KB pages, so every
 * Hilt test (they all install `FakeFirebaseModule`'s mocks) fails on that leg for a reason that
 * says nothing about the app. The leg runs only non-Hilt tests (see `ci.yml`), and this class is
 * what gives it something to prove: SQLCipher (`libsqlcipher.so`, the SEC-2 database), ML Kit
 * text recognition (receipt OCR) and ML Kit barcode scanning (the pairing QR). The Hilt tests keep
 * running on API 26 and 30.
 */
@RunWith(AndroidJUnit4::class)
class NativeLibrariesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun sqlCipher_loads_andWritesAnEncryptedDatabase() {
        System.loadLibrary("sqlcipher")
        val database = Room.databaseBuilder(context, CoPlanlyDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(PASSPHRASE.toByteArray(Charsets.US_ASCII)))
            .build()
        val tables = database.openHelper.writableDatabase
            .query("SELECT count(*) FROM sqlite_master WHERE type = 'table'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        database.close()

        assertTrue("the schema was created through SQLCipher", tables > 0)
        val header = File(context.getDatabasePath(DATABASE_NAME).path).inputStream().use {
            ByteArray(PLAINTEXT_HEADER.length).also { bytes -> it.read(bytes) }
        }
        assertFalse(
            "the file is not plaintext SQLite",
            String(header, Charsets.US_ASCII) == PLAINTEXT_HEADER
        )
    }

    @Test
    fun mlKitTextRecognition_loads_andReadsABlankImage() = runBlocking {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = withTimeout(TIMEOUT_MS) { recognizer.process(blankImage()).await() }
        recognizer.close()

        assertEquals("a blank image holds no text", "", text.text)
    }

    @Test
    fun mlKitBarcodeScanning_loads_andFindsNothingInABlankImage() = runBlocking {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val barcodes = withTimeout(TIMEOUT_MS) { scanner.process(blankImage()).await() }
        scanner.close()

        assertTrue("a blank image holds no QR code", barcodes.isEmpty())
    }

    private fun blankImage(): InputImage = InputImage.fromBitmap(
        Bitmap.createBitmap(IMAGE_SIDE_PX, IMAGE_SIDE_PX, Bitmap.Config.ARGB_8888),
        0
    )

    private companion object {
        const val DATABASE_NAME = "native_libraries_test.db"
        const val PASSPHRASE = "native-libraries-test"
        const val PLAINTEXT_HEADER = "SQLite format 3"
        const val TIMEOUT_MS = 30_000L
        const val IMAGE_SIDE_PX = 64
    }
}
