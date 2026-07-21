package com.coparently.app.domain.repository

/**
 * Abstraction over remote binary storage for receipt photos.
 *
 * Implementations upload a locally picked image (referenced by a content URI string,
 * kept as [String] so the domain layer stays free of Android types) and return a
 * publicly resolvable download URL that can be shared with the other parent.
 */
interface ReceiptStorage {

    /**
     * Uploads the image behind [localUri] as the receipt for [expenseId].
     *
     * @param expenseId Expense the receipt belongs to; used as the remote object name
     *                  so re-uploading replaces the previous receipt.
     * @param localUri Content URI string of the picked image on this device.
     * @return Download URL of the uploaded receipt.
     */
    suspend fun uploadReceipt(expenseId: String, localUri: String): String

    /**
     * Deletes the remote receipt for [expenseId], if any. Safe to call when none exists.
     */
    suspend fun deleteReceipt(expenseId: String)
}
