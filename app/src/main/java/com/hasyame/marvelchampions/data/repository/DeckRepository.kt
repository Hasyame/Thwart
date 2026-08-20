package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.SavedDeckDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.deckbuilder.HeroDeckRulesParser
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.dto.DeckDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.DeckMetaDto
import com.hasyame.marvelchampions.domain.deeplink.DeckReference
import com.hasyame.marvelchampions.domain.model.CardLocale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Why an import failed, in terms the UI can explain without an HTTP code. */
sealed interface DeckImportError {
    /** The text was not a MarvelCDB deck link. */
    data object NotADeckLink : DeckImportError

    /** No such published decklist. */
    data object NotFound : DeckImportError

    /**
     * A personal deck that is private, or does not exist. MarvelCDB redirects
     * both to the login page, so the two genuinely cannot be told apart.
     */
    data object NotShared : DeckImportError

    data object Network : DeckImportError

    /** A deck built in the app has nothing to refresh from. */
    data object LocalDeck : DeckImportError

    data class Unexpected(val message: String?) : DeckImportError
}

sealed interface DeckImportResult {
    data class Success(val deckId: String) : DeckImportResult
    data class Failure(val error: DeckImportError) : DeckImportResult
}

/** One row of a deck listing, resolved against the card database. */
data class DeckCard(
    val card: CardEntity,
    val quantity: Int,
    /** True when no pack supplying this card is marked as owned. */
    val missingFromCollection: Boolean,
)

data class DeckContents(
    val deck: SavedDeckEntity,
    val hero: CardEntity?,
    val cardsByType: Map<String, List<DeckCard>>,
    val totalCards: Int,
    val missingCards: List<DeckCard>,
    /** Codes in the deck that the local card database does not know. */
    val unknownCardCodes: List<String>,
)

@Singleton
class DeckRepository @Inject constructor(
    private val api: MarvelCdbApi,
    private val savedDeckDao: SavedDeckDao,
    private val cardDao: CardDao,
    private val collectionRepository: CollectionRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeDecks(): Flow<List<SavedDeckEntity>> = savedDeckDao.observeDecks()

    fun observeDeck(id: String): Flow<SavedDeckEntity?> = savedDeckDao.observeDeck(id)

    suspend fun getDecks(): List<SavedDeckEntity> =
        withContext(ioDispatcher) { savedDeckDao.getDecks() }

    suspend fun delete(id: String) = withContext(ioDispatcher) { savedDeckDao.delete(id) }

    suspend fun import(reference: DeckReference): DeckImportResult = withContext(ioDispatcher) {
        try {
            val response = api.getDeckRaw(reference.apiUrl)

            // A redirect means MarvelCDB sent us to the login page, which is
            // what it does for a private or missing personal deck.
            if (response.raw().priorResponse != null) {
                return@withContext DeckImportResult.Failure(DeckImportError.NotShared)
            }
            if (!response.isSuccessful) {
                return@withContext DeckImportResult.Failure(
                    DeckImportError.Unexpected("HTTP ${response.code()}"),
                )
            }

            val body = response.body()?.string().orEmpty()
            // A published decklist that does not exist comes back as 200 with
            // a zero-length body rather than a 404.
            if (body.isBlank()) {
                return@withContext DeckImportResult.Failure(DeckImportError.NotFound)
            }
            if (!body.trimStart().startsWith("{")) {
                // HTML, so we were served a page rather than the API.
                return@withContext DeckImportResult.Failure(DeckImportError.NotShared)
            }

            val dto = json.decodeFromString(DeckDto.serializer(), body)
            val entity = withCompleteAspects(dto.toEntity(reference, body))
            savedDeckDao.upsert(entity)
            DeckImportResult.Success(entity.id)
        } catch (_: IOException) {
            DeckImportResult.Failure(DeckImportError.Network)
        } catch (error: Exception) {
            DeckImportResult.Failure(DeckImportError.Unexpected(error.message))
        }
    }

    /** Re-fetches a stored deck, keeping the same local id. */
    suspend fun refresh(id: String): DeckImportResult {
        val existing = withContext(ioDispatcher) { savedDeckDao.getDeck(id) }
            ?: return DeckImportResult.Failure(DeckImportError.NotFound)
        if (existing.kind == LOCAL_KIND) {
            // A deck built in the app has no MarvelCDB counterpart to refresh
            // from; refreshing it would mean overwriting it with nothing.
            return DeckImportResult.Failure(DeckImportError.LocalDeck)
        }
        val kind = DeckReference.Kind.entries.firstOrNull { it.name == existing.kind }
            ?: return DeckImportResult.Failure(DeckImportError.Unexpected(existing.kind))
        return import(DeckReference(existing.marvelCdbId, kind))
    }

    /** Creates an empty deck built in the app rather than imported. */
    suspend fun createLocalDeck(
        name: String,
        heroCode: String,
        heroName: String,
        aspects: List<String>,
        /**
         * Cards the deck starts with, by code and quantity.
         *
         * A hero's own cards are not a choice — the deck must hold all of them
         * in exactly those numbers — so a new deck starts with them in it
         * rather than opening empty and failing validation until the player
         * adds by hand what was never optional.
         */
        slots: Map<String, Int> = emptyMap(),
    ): String = withContext(ioDispatcher) {
        val id = "$LOCAL_ID_PREFIX${UUID.randomUUID()}"
        savedDeckDao.upsert(
            SavedDeckEntity(
                id = id,
                marvelCdbId = 0L,
                kind = LOCAL_KIND,
                url = "",
                name = name,
                heroCode = heroCode,
                heroName = heroName,
                aspects = aspects.joinToString(","),
                slots = slots.entries.joinToString(",") { "${it.key}=${it.value}" },
                ignoreDeckLimitSlots = "",
                descriptionMd = null,
                version = null,
                tags = null,
                rawJson = "",
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )
        id
    }

    /**
     * Changes how many copies of a card a locally built deck holds.
     *
     * Only local decks are editable: an imported deck must stay a faithful copy
     * of what MarvelCDB has, so that a refresh never silently discards edits.
     */
    suspend fun setCardQuantity(deckId: String, cardCode: String, quantity: Int): Boolean =
        withContext(ioDispatcher) {
            val deck = savedDeckDao.getDeck(deckId) ?: return@withContext false
            val slots = parseSlots(deck.slots).toMutableMap()
            if (quantity <= 0) {
                slots.remove(cardCode)
            } else {
                slots[cardCode] = quantity
            }
            savedDeckDao.upsert(
                deck.copy(
                    slots = slots.entries.joinToString(",") { "${it.key}=${it.value}" },
                    // Remembered so a later refresh can warn rather than
                    // quietly throwing the change away.
                    locallyEdited = true,
                ),
            )
            true
        }

    suspend fun renameDeck(deckId: String, name: String) = withContext(ioDispatcher) {
        savedDeckDao.getDeck(deckId)?.let {
            savedDeckDao.upsert(it.copy(name = name, locallyEdited = true))
        }
    }

    /** Puts an imported deck back to exactly what MarvelCDB returned. */
    suspend fun revertToImported(deckId: String): Boolean = withContext(ioDispatcher) {
        val deck = savedDeckDao.getDeck(deckId) ?: return@withContext false
        if (deck.kind == LOCAL_KIND) {
            return@withContext false
        }
        val dto = runCatching {
            json.decodeFromString(DeckDto.serializer(), deck.rawJson)
        }.getOrNull() ?: return@withContext false

        savedDeckDao.upsert(
            deck.copy(
                slots = dto.slots.entries.joinToString(",") { "${it.key}=${it.value}" },
                locallyEdited = false,
            ),
        )
        true
    }

    /**
     * Fills in aspects an imported deck could not have carried.
     *
     * MarvelCDB's metadata has room for two aspects — `aspect` and `aspect2` —
     * and Adam Warlock takes four. His imported decks therefore arrived naming
     * two of them, which read as a two-aspect deck: the app displayed
     * "Aggression / Justice" and called the other half of his cards illegal.
     *
     * The cards themselves know better, so the rest is read off them. Only ever
     * adds; an aspect the deck's author recorded is never dropped.
     */
    private suspend fun withCompleteAspects(deck: SavedDeckEntity): SavedDeckEntity {
        val hero = cardDao.getCard(deck.heroCode, CardLocale.ENGLISH.code)
            ?: cardDao.getCardPreferringLocale(deck.heroCode, CardLocale.ENGLISH.code)
            ?: return deck
        val needed = HeroDeckRulesParser.parse(hero, json).aspectCount
        val recorded = parseAspects(deck.aspects)
        if (recorded.size >= needed) {
            return deck
        }

        val fromCards = parseSlots(deck.slots).keys
            .mapNotNull { cardDao.getCardPreferringLocale(it, CardLocale.ENGLISH.code) }
            .map { it.factionCode }
            .filter { it in ASPECT_FACTIONS }
            .distinct()

        val complete = (recorded + fromCards).distinct().take(needed)
        return if (complete.size > recorded.size) {
            deck.copy(aspects = complete.joinToString(","))
        } else {
            deck
        }
    }

    suspend fun setAspects(deckId: String, aspects: List<String>) = withContext(ioDispatcher) {
        savedDeckDao.getDeck(deckId)?.let {
            savedDeckDao.upsert(it.copy(aspects = aspects.joinToString(",")))
        }
    }

    suspend fun getDeck(id: String): SavedDeckEntity? =
        withContext(ioDispatcher) { savedDeckDao.getDeck(id) }

    /**
     * Resolves a stored deck against the card database.
     *
     * Works entirely offline: the deck rows and the card rows are both local,
     * so this never touches the network.
     */
    suspend fun contents(id: String, locale: CardLocale): DeckContents? =
        withContext(ioDispatcher) {
            val deck = savedDeckDao.getDeck(id) ?: return@withContext null
            val owned = collectionRepository.getOwnedCodes()
            val slots = parseSlots(deck.slots)

            val resolved = mutableListOf<DeckCard>()
            val unknown = mutableListOf<String>()
            for ((code, quantity) in slots) {
                val card = cardDao.getCardPreferringLocale(code, locale.code)
                if (card == null) {
                    unknown += code
                } else {
                    resolved += DeckCard(
                        card = card,
                        quantity = quantity,
                        missingFromCollection = card.packCode !in owned,
                    )
                }
            }

            DeckContents(
                deck = deck,
                hero = cardDao.getCard(deck.heroCode, locale.code),
                cardsByType = resolved
                    .sortedWith(compareBy({ it.card.typeName }, { it.card.name }))
                    .groupBy { it.card.typeName },
                totalCards = slots.values.sum(),
                missingCards = resolved.filter { it.missingFromCollection },
                unknownCardCodes = unknown,
            )
        }

    private fun DeckDto.toEntity(reference: DeckReference, raw: String): SavedDeckEntity {
        val aspects = meta
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching { json.decodeFromString(DeckMetaDto.serializer(), it) }.getOrNull()
            }
            ?.aspects
            .orEmpty()

        return SavedDeckEntity(
            id = localId(reference),
            marvelCdbId = id,
            kind = reference.kind.name,
            url = reference.apiUrl,
            name = name,
            heroCode = heroCode,
            heroName = heroName,
            aspects = aspects.joinToString(","),
            slots = slots.entries.joinToString(",") { "${it.key}=${it.value}" },
            ignoreDeckLimitSlots = ignoreDeckLimitSlots.orEmpty().entries
                .joinToString(",") { "${it.key}=${it.value}" },
            descriptionMd = descriptionMd,
            version = version,
            tags = tags,
            rawJson = raw,
            lastSyncedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        /** Marks a deck built in the app rather than imported. */
        const val LOCAL_KIND: String = "LOCAL"

        /** The four aspects a deck can be customised from. */
        private val ASPECT_FACTIONS =
            setOf("aggression", "justice", "leadership", "protection")
        private const val LOCAL_ID_PREFIX = "local-"

        fun isLocal(deck: SavedDeckEntity): Boolean = deck.kind == LOCAL_KIND

        /** Namespaced because decklist 30000 and deck 30000 are different decks. */
        fun localId(reference: DeckReference): String =
            "${reference.kind.name.lowercase()}-${reference.id}"

        fun parseSlots(stored: String): Map<String, Int> = stored
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split('=')
                val quantity = parts.getOrNull(1)?.toIntOrNull()
                // A blank code would otherwise survive as an empty-string key
                // and produce a phantom card in the deck listing.
                if (parts.size == 2 && parts[0].isNotBlank() && quantity != null) {
                    parts[0] to quantity
                } else {
                    null
                }
            }
            .toMap()

        fun parseAspects(stored: String): List<String> =
            stored.split(',').filter { it.isNotBlank() }
    }
}
