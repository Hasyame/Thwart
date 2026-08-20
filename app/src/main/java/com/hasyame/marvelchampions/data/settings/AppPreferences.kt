package com.hasyame.marvelchampions.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User preferences.
 *
 * The **card data language is deliberately separate from the UI language** —
 * reading a card in English while the app is in French is a stated
 * requirement, not an accident.
 */
@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val cardLocale: Flow<CardLocale> = context.dataStore.data.map { preferences ->
        preferences[KEY_CARD_LOCALE]?.let(CardLocale::fromCode) ?: CardLocale.FRENCH
    }

    val lastCardSync: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_SYNC] }

    /**
     * Whether a game in progress counts villain health and scheme threat.
     *
     * Off by default, and a choice rather than an improvement: plenty of people
     * want the app to time the game and nothing else, and dice on the table
     * work fine. Turning it on also keeps the screen awake, because a tracker
     * that has locked itself is worse than no tracker.
     */
    val trackEncounter: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TRACK_ENCOUNTER] ?: false
    }

    /**
     * Where games get played, as free text.
     *
     * BoardGameGeek's location on a play is a string a person wrote — "Home",
     * "Chez Marc", the name of a club — not a coordinate, so this is typed
     * rather than sensed. Asking Android for the position would mean a location
     * permission on an app that has only ever asked for the network, to produce
     * something less useful than the word you would have typed.
     *
     * A setting rather than a question at the end of every game: most people
     * play in the same handful of places, and a prompt you answer identically
     * every time is a prompt worth not asking.
     */
    val playLocation: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PLAY_LOCATION].orEmpty()
    }

    /** Light, dark, or whatever the system is doing. */
    val themeChoice: Flow<ThemeChoice> = context.dataStore.data.map { preferences ->
        ThemeChoice.fromCode(preferences[KEY_THEME])
    }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        context.dataStore.edit { it[KEY_THEME] = choice.code }
    }

    suspend fun currentCardLocale(): CardLocale = cardLocale.first()

    suspend fun setCardLocale(locale: CardLocale) {
        context.dataStore.edit { it[KEY_CARD_LOCALE] = locale.code }
    }

    suspend fun setLastCardSync(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = epochMillis }
    }

    suspend fun setPlayLocation(location: String) {
        context.dataStore.edit { preferences ->
            if (location.isBlank()) {
                preferences.remove(KEY_PLAY_LOCATION)
            } else {
                preferences[KEY_PLAY_LOCATION] = location.trim()
            }
        }
    }

    suspend fun currentPlayLocation(): String = playLocation.first()

    suspend fun setTrackEncounter(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_TRACK_ENCOUNTER] = enabled }
    }

    companion object {
        private val KEY_CARD_LOCALE = stringPreferencesKey("card_locale")
        private val KEY_LAST_SYNC = longPreferencesKey("last_card_sync")
        private val KEY_THEME = stringPreferencesKey("theme_choice")
        private val KEY_PLAY_LOCATION = stringPreferencesKey("play_location")
        private val KEY_TRACK_ENCOUNTER = booleanPreferencesKey("track_encounter")
    }
}
