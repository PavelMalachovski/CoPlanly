package com.coparently.app.data.export

import android.util.Log
import com.coparently.app.BuildConfig
import com.coparently.app.domain.export.ExportFormat
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The client half of MON-16: reserving the record id an export prints, and registering the
 * SHA-256 of the file once it is rendered (`functions/export-receipts.js`).
 *
 * **Every failure is an answer, never an exception.** An export must still work offline — it is
 * made on the phone — so a call that fails, times out or is refused returns null or false, and
 * the export flow prints "not registered" instead of an id. The worst outcome is a file that
 * honestly says it cannot be checked; the one outcome this class must never allow is a file that
 * names an id the server holds no hash for.
 */
@Singleton
class ExportReceipts @Inject constructor(
    private val functions: FirebaseFunctions
) {

    /**
     * The verification page's address, or blank while none is hosted. Blank omits the address
     * from the file rather than printing one that does not resolve (design item 8).
     */
    val verifyUrl: String
        get() = BuildConfig.EXPORT_VERIFY_URL.trim()

    /**
     * Asks the server for a record id for an export about to be rendered.
     *
     * @param familyId `FamilyKey.of` of the pair, or blank for an account with no co-parent.
     * @return The id (16 Crockford base-32 characters), or null when the server could not be
     *   reached or refused.
     */
    suspend fun reserve(familyId: String, from: LocalDate, to: LocalDate, format: ExportFormat): String? {
        val payload = mapOf(
            "familyId" to familyId,
            "fromDate" to from.toString(),
            "toDate" to to.toString(),
            "format" to format.wireName
        )
        return call(RESERVE, payload)?.get("recordId") as? String
    }

    /**
     * Registers the hash of the rendered file under [recordId]. True only when the server
     * confirms it holds exactly this hash — a retry of a registration that already landed
     * confirms too.
     */
    suspend fun register(recordId: String, sha256: String, byteLength: Int): Boolean {
        val payload = mapOf("recordId" to recordId, "sha256" to sha256, "byteLength" to byteLength)
        val answer = call(REGISTER, payload) ?: return false
        return answer["recordId"] == recordId && answer["recordedAtMillis"] is Number
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<*, *>? = try {
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            functions.getHttpsCallable(name).call(payload).await().getData() as? Map<*, *>
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Offline, refused, or a backend not yet deployed: all mean "not registered", which the
        // file then says. Rethrowing would fail an export that works without the server.
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "$name failed; the export will say it is not registered", e)
        null
    }

    private companion object {
        const val TAG = "ExportReceipts"
        const val RESERVE = "reserveExportRecordId"
        const val REGISTER = "registerExportReceipt"

        /** Long enough for a slow network, short enough that a parent is not left waiting offline. */
        const val CALL_TIMEOUT_MS = 10_000L
    }
}
