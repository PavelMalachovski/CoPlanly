package com.coparently.app.data.export

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.time.IsoText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate

/**
 * The client half of MON-16: reserving the record id an export prints, and registering the
 * SHA-256 of the file once it is rendered (`functions/export-receipts.js`).
 *
 * **Every failure is an answer, never an exception.** An export must still work offline — it is
 * made on the phone — so a call that fails, times out or is refused returns null or false, and
 * the export flow prints "not registered" instead of an id. The worst outcome is a file that
 * honestly says it cannot be checked; the one outcome this class must never allow is a file that
 * names an id the server holds no hash for.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j), on [CallableFunctions] (the Functions SDK's
 * `getHttpsCallable(name).call(payload)` on Android, as before), with the verification page's
 * address from the platform's [verifyAddress] (the app's `BuildConfig.EXPORT_VERIFY_URL`). The
 * period's dates are written as `LocalDate.toString()` wrote them (`IsoText.date`).
 */
class ExportReceipts(
    private val functions: CallableFunctions,
    private val verifyAddress: ExportVerifyAddress
) {

    /**
     * The verification page's address, or blank while none is hosted. Blank omits the address
     * from the file rather than printing one that does not resolve (design item 8).
     */
    val verifyUrl: String
        get() = verifyAddress.configured().trim()

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
            "fromDate" to IsoText.date(from),
            "toDate" to IsoText.date(to),
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
            functions.call(name, payload) as? Map<*, *>
        }
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Offline, refused, or a backend not yet deployed: all mean "not registered", which the
        // file then says. Rethrowing would fail an export that works without the server.
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.w(TAG, "$name failed; the export will say it is not registered", e)
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

/**
 * Where an export's verification page is hosted, as the platform's build configures it (MON-16;
 * MON-24 phase 2, Task 8j): the app's `BuildConfig.EXPORT_VERIFY_URL`, blank while `web/verify/` is
 * not hosted. The iPhone's says blank until it has one, which leaves the line off the file.
 */
fun interface ExportVerifyAddress {

    /** The configured address, possibly blank or padded; [ExportReceipts.verifyUrl] trims it. */
    fun configured(): String
}
