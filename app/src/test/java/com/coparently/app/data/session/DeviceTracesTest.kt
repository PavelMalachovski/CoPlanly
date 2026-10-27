package com.coparently.app.data.session

import android.app.Application
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an account leaves outside the encrypted database goes with it (security audit, October
 * 2026): sign-out removes the plaintext file copies and keeps the outbox Room still refers to; an
 * account switch or deletion removes both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceTracesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var medicalPhoto: File
    private lateinit var export: File
    private lateinit var receipt: File
    private lateinit var paymentCode: File
    private lateinit var outboxFile: File
    private lateinit var unrelated: File

    @Before
    fun seed() {
        medicalPhoto = file(context.cacheDir, "shared_files/view/${"a".repeat(64)}/allergy.jpg")
        export = file(context.cacheDir, "exports/record.pdf")
        receipt = file(context.cacheDir, "receipts/receipt_1.jpg")
        paymentCode = file(context.cacheDir, "payment_codes/code.png")
        outboxFile = file(context.filesDir, "chat_outbox/m1/consent.pdf")
        unrelated = file(context.cacheDir, "image_cache/avatar")
    }

    @Test
    fun `sign-out removes the plaintext copies and keeps the outbox`() = runTest {
        DeviceTraces(context).clearAfterSignOut()

        listOf(medicalPhoto, export, receipt, paymentCode).forEach { assertFalse(it.exists(), it.path) }
        assertFalse(File(context.cacheDir, "shared_files").exists())
        assertTrue(outboxFile.exists(), "a message still waiting to send keeps its file")
        assertTrue(unrelated.exists(), "only the account's own directories go")
    }

    @Test
    fun `an account switch or deletion removes the outbox too`() = runTest {
        DeviceTraces(context).clearAccount()

        listOf(medicalPhoto, export, receipt, paymentCode, outboxFile).forEach {
            assertFalse(it.exists(), it.path)
        }
        assertTrue(unrelated.exists())
    }

    @Test
    fun `clearing a phone that holds nothing is not an error`() = runTest {
        DeviceTraces(context).clearAccount()
        DeviceTraces(context).clearAccount()
        DeviceTraces(context).clearAfterSignOut()
    }

    private fun file(root: File, path: String): File =
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText("bytes")
        }
}
