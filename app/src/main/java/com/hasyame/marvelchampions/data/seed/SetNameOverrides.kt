package com.hasyame.marvelchampions.data.seed

import android.content.Context
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Corrections to the set names the card database supplies.
 *
 * MarvelCDB's French data leaves a good number of encounter set names in
 * English — "Escape the Museum" rather than "Fuir le Musée" — and the app was
 * faithfully showing what it was given. Waiting for upstream is not a fix for
 * somebody holding the French cards in front of them.
 *
 * Deliberately a correction rather than a translation table: a code absent from
 * the file keeps whatever the card database says. Most names are proper nouns
 * that should not be translated at all, and a file that tried to hold every set
 * name would rot the moment a pack shipped.
 */
@Singleton
class SetNameOverrides @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    // Concurrent because the reads are not: the card list and the collection
    // screen both ask for corrections, on the IO dispatcher, at the same
    // moment. `getOrPut` on a plain HashMap from two threads can corrupt it,
    // and the failure that follows is a hang rather than an exception.
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    /** Code to corrected name, for one locale. Empty when there is nothing to fix. */
    suspend fun forLocale(locale: CardLocale): Map<String, String> =
        corrections(locale.code, section = null)

    /**
     * The same, for pack names.
     *
     * Kept apart from the set corrections rather than sharing one table, because
     * thirty-four codes mean both things at once: `deadpool` is a hero pack and
     * also an encounter set, `magneto` and `gambit` likewise. One table would
     * have renamed the set whenever the pack was corrected.
     */
    suspend fun packsForLocale(locale: CardLocale): Map<String, String> =
        corrections(locale.code, section = PACKS)

    private suspend fun corrections(
        localeCode: String,
        section: String?,
    ): Map<String, String> = withContext(ioDispatcher) {
        cache.getOrPut(section.orEmpty() + "/" + localeCode) {
            runCatching {
                val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
                // A safe cast, not jsonObject: the file carries a _note at the
                // top level, and anything that is not a block of corrections
                // should read as "no corrections" rather than throwing.
                val root = json.parseToJsonElement(text).jsonObject
                val scope = if (section == null) root else root[section] as? JsonObject
                (scope?.get(localeCode) as? JsonObject)
                    ?.mapValues { (_, value) -> value.jsonPrimitive.content }
                    .orEmpty()
            }.getOrDefault(emptyMap())
        }
    }

    private companion object {
        const val ASSET = "set_name_overrides.json"
        const val PACKS = "packs"
    }
}
