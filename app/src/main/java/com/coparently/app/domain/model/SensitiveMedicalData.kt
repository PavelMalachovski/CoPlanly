package com.coparently.app.domain.model

import com.coparently.app.data.security.EncryptionManager

/**
 * Domain model for sensitive medical data that needs to be encrypted.
 * Contains methods to convert to/from encrypted format.
 */
data class SensitiveMedicalData(
    val childId: String,
    val diagnosis: String,
    val treatment: String,
    val medications: List<String>,
    val notes: String? = null
) {
    /**
     * Encrypts all sensitive fields and returns an EncryptedMedicalData instance.
     *
     * @param encryptionManager The encryption manager to use
     * @return EncryptedMedicalData with all sensitive fields encrypted
     */
    fun encryptSensitiveFields(encryptionManager: EncryptionManager): EncryptedMedicalData {
        return EncryptedMedicalData(
            childId = childId,
            encryptedDiagnosis = encryptionManager.encrypt(diagnosis),
            encryptedTreatment = encryptionManager.encrypt(treatment),
            encryptedMedications = encryptionManager.encryptList(medications),
            encryptedNotes = notes?.let { encryptionManager.encrypt(it) }
        )
    }
}

/**
 * Encrypted version of medical data for storage.
 * All sensitive fields are encrypted using AES-256-GCM.
 */
data class EncryptedMedicalData(
    val childId: String,
    val encryptedDiagnosis: String,
    val encryptedTreatment: String,
    val encryptedMedications: List<String>,
    val encryptedNotes: String?
) {
    /**
     * Decrypts all sensitive fields and returns a SensitiveMedicalData instance.
     *
     * @param encryptionManager The encryption manager to use
     * @return SensitiveMedicalData with all fields decrypted
     */
    fun decryptSensitiveFields(encryptionManager: EncryptionManager): SensitiveMedicalData {
        return SensitiveMedicalData(
            childId = childId,
            diagnosis = encryptionManager.decrypt(encryptedDiagnosis),
            treatment = encryptionManager.decrypt(encryptedTreatment),
            medications = encryptionManager.decryptList(encryptedMedications),
            notes = encryptedNotes?.let { encryptionManager.decrypt(it) }
        )
    }
}
