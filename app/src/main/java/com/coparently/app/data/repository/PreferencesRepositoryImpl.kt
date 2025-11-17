package com.coparently.app.data.repository

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PreferencesRepository.
 * Manages user preferences using EncryptedPreferences.
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences
) : PreferencesRepository {

    private val _darkThemeFlow = MutableStateFlow<Boolean?>(null)

    init {
        // Initialize with current value
        _darkThemeFlow.value = encryptedPreferences.getDarkTheme()
    }

    override fun getDarkThemeFlow(): Flow<Boolean?> {
        return _darkThemeFlow.asStateFlow()
    }

    override suspend fun getDarkTheme(): Boolean? {
        return encryptedPreferences.getDarkTheme()
    }

    override suspend fun setDarkTheme(isDarkTheme: Boolean) {
        encryptedPreferences.putDarkTheme(isDarkTheme)
        _darkThemeFlow.value = isDarkTheme
    }

    override suspend fun clearDarkTheme() {
        encryptedPreferences.clearDarkTheme()
        _darkThemeFlow.value = null
    }
}

