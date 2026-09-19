package com.hasyame.marvelchampions.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InitialThemeTest {
    private val initial = stringPreferencesKey("initial_theme_choice")
    private val theme = stringPreferencesKey("theme_choice")

    @Test fun `new installs follow the system while upgrades keep dark`() = runTest {
        assertEquals("system", initialThemeMigration(false).migrate(emptyPreferences())[initial])
        assertEquals("dark", initialThemeMigration(true).migrate(emptyPreferences())[initial])
        val fresh = initialThemeMigration(false).migrate(emptyPreferences())
        assertFalse(fresh.contains(theme))
        assertFalse(initialThemeMigration(true).shouldMigrate(fresh))
        assertEquals(fresh, initialThemeMigration(true).migrate(fresh))
        val restored = preferencesOf(stringPreferencesKey("card_locale") to "fr")
        assertEquals("dark", initialThemeMigration(false).migrate(restored)[initial])
    }

    @Test fun `explicit choices are never rewritten`() = runTest {
        for (choice in listOf("system", "light", "dark")) {
            val saved = preferencesOf(theme to choice)
            assertFalse(initialThemeMigration(true).shouldMigrate(saved))
            assertEquals(saved, initialThemeMigration(true).migrate(saved))
        }
    }
}
