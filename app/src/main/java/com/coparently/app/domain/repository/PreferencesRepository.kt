package com.coparently.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user preferences.
 * Abstracts the data layer for application settings.
 */
interface PreferencesRepository {
    /**
     * Gets the dark theme preference as a Flow.
     * Returns null if not set (use system default).
     *
     * @return Flow emitting true for dark theme, false for light theme, null for system default
     */
    fun getDarkThemeFlow(): Flow<Boolean?>

    /**
     * Gets the current dark theme preference.
     * Returns null if not set (use system default).
     *
     * @return True for dark theme, false for light theme, null for system default
     */
    suspend fun getDarkTheme(): Boolean?

    /**
     * Sets the dark theme preference.
     *
     * @param isDarkTheme True to enable dark theme, false to enable light theme
     */
    suspend fun setDarkTheme(isDarkTheme: Boolean)

    /**
     * Clears the dark theme preference (reverts to system default).
     */
    suspend fun clearDarkTheme()
}

