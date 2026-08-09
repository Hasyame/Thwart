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

/** One keyword of the Rules Reference, in both languages. */
@Serializable
data class RuleEntryDto(
    val term: String,
    /** The keyword as the French cards print it — "Riposte X" for retaliate X. */
    val termFr: String? = null,
    val en: String,
    val fr: String? = null,
)

@Serializable
data class RulesReferenceFileDto(
    val source: String = "",
    val sourceCredit: String = "",
    val sourceLicence: String = "",
    val entries: List<RuleEntryDto> = emptyList(),
)

/** Who compiled the reference this is built from, so the screen can credit them. */
data class RulesCredit(
    val name: String,
    val source: String,
    val licence: String,
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
 * Read once and kept: a couple of hundred entries is small, and a lookup during
 * a game should not wait on a file.
 */
@Singleton
class RulesReference @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private var cached: RulesReferenceFileDto? = null

    suspend fun entries(locale: CardLocale): List<RuleEntry> = withContext(ioDispatcher) {
        val french = locale == CardLocale.FRENCH
        load().entries.map { entry ->
            val body = entry.fr?.takeIf { french && it.isNotBlank() }
            // The keyword is translated too. A French player looking up the word
            // printed on their card is looking for "Riposte", not "retaliate".
            val term = entry.termFr?.takeIf { french && it.isNotBlank() }
            RuleEntry(
                term = term ?: entry.term,
                body = body ?: entry.en,
                untranslated = french && body == null,
            )
        }
    }

    suspend fun source(): String = load().source

    /** Who compiled the reference, for the line of credit under the list. */
    suspend fun credit(): RulesCredit? = load().takeIf { it.sourceCredit.isNotBlank() }
        ?.let { RulesCredit(it.sourceCredit, it.source, it.sourceLicence) }

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
