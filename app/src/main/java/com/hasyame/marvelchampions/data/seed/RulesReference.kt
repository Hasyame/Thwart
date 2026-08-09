package com.hasyame.marvelchampions.data.seed

import android.content.Context
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** One keyword of the Rules Reference. */
@Serializable
data class RuleEntryDto(
    val term: String,
    val en: String,
    /** Null until somebody translates it; MarvelCDB publishes no French rules. */
    val fr: String? = null,
)

@Serializable
data class RulesReferenceFileDto(
    val source: String = "",
    val entries: List<RuleEntryDto> = emptyList(),
)

/** A keyword and the text to show for it, in the language asked for. */
data class RuleEntry(
    val term: String,
    val body: String,
    /** True when only the English text exists, so the screen can say so. */
    val untranslated: Boolean,
)

/**
 * The Rules Reference, bundled so it works at a table with no signal.
 *
 * Which is the whole point: the moment somebody needs to know what *retaliate*
 * does is the moment four people are staring at a card, and a rulebook is in a
 * bag under the table.
 *
 * Read once and kept: 143 entries is small, and a lookup during a game should
 * not wait on a file.
 */
@Singleton
class RulesReference @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private var cached: RulesReferenceFileDto? = null

    suspend fun entries(locale: CardLocale): List<RuleEntry> = withContext(ioDispatcher) {
        load().entries.map { entry ->
            val translated = entry.fr?.takeIf { locale == CardLocale.FRENCH && it.isNotBlank() }
            RuleEntry(
                term = entry.term,
                body = translated ?: entry.en,
                untranslated = locale == CardLocale.FRENCH && translated == null,
            )
        }
    }

    suspend fun source(): String = load().source

    private suspend fun load(): RulesReferenceFileDto = withContext(ioDispatcher) {
        cached ?: runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString(RulesReferenceFileDto.serializer(), text)
        }.getOrDefault(RulesReferenceFileDto()).also { cached = it }
    }

    private companion object {
        const val ASSET = "rules_reference.json"
    }
}
