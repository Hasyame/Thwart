package com.hasyame.marvelchampions.data.repository

import android.util.Log
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.DraftSessionDao
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.DraftSessionEntity
import com.hasyame.marvelchampions.data.deckbuilder.toDeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition
import com.hasyame.marvelchampions.domain.draft.DraftCard
import com.hasyame.marvelchampions.domain.draft.DraftContext
import com.hasyame.marvelchampions.domain.draft.DraftNaming
import com.hasyame.marvelchampions.domain.draft.DraftRules
import com.hasyame.marvelchampions.domain.draft.DraftState
import com.hasyame.marvelchampions.domain.draft.DraftStockBuilder
import com.hasyame.marvelchampions.domain.draft.StockRow
import com.hasyame.marvelchampions.domain.model.CardLocale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** An identity on the shelf, as the identity page shows it. */
data class DraftHero(
    val card: CardEntity,
    val rules: HeroDeckRules,
)

/** How a finished draft ended: as decks, or as a bug report. */
sealed interface DraftOutcome {
    data class Saved(val deckIds: List<String>) : DraftOutcome

    /** A deck the standard validation refused. Never saved silently. */
    data class Illegal(val playerIndex: Int, val problems: List<DeckProblem>) : DraftOutcome
}

/**
 * The draft's storage and its card data: the session row, the shelf built
 * from the collection, each identity's rules, and the decks at the end.
 */
@Singleton
class DraftRepository @Inject constructor(
    private val sessionDao: DraftSessionDao,
    private val cardDao: CardDao,
    private val ownedPackDao: OwnedPackDao,
    private val deckRepository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    // --- the session --------------------------------------------------------------

    fun observeSession(): Flow<DraftState?> = sessionDao.observe().map { it?.let(::decode) }

    suspend fun currentSession(): DraftState? = withContext(ioDispatcher) {
        sessionDao.current()?.let(::decode)
    }

    suspend fun save(state: DraftState) = withContext(ioDispatcher) {
        sessionDao.upsert(
            DraftSessionEntity(
                stateJson = json.encodeToString(DraftState.serializer(), state),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clear() = withContext(ioDispatcher) { sessionDao.clear() }

    private fun decode(entity: DraftSessionEntity): DraftState? =
        runCatching { json.decodeFromString(DraftState.serializer(), entity.stateJson) }
            .onFailure { Log.w(TAG, "unreadable draft session, dropped", it) }
            .getOrNull()

    // --- the shelf -----------------------------------------------------------------

    /** The identities the collection holds, with their deck building rules. */
    suspend fun ownedHeroes(locale: CardLocale): List<DraftHero> = withContext(ioDispatcher) {
        builderRepository.heroes(locale)
            .filter { it.owned }
            .mapNotNull { choice ->
                builderRepository.heroRules(choice.card.code, locale)?.let { DraftHero(choice.card, it) }
            }
    }

    /**
     * The card data behind a draft: the shelf, and the rules and traits of
     * every identity in it. Built from the database each time the draft is
     * opened, since none of it is written down with the state.
     */
    suspend fun context(state: DraftState, locale: CardLocale): DraftContext = withContext(ioDispatcher) {
        val owned = ownedPackDao.getOwned().associate { it.packCode to it.quantity }
        val stock = DraftStockBuilder.build(stockRows(locale), owned)

        val heroCodes = state.players.mapNotNull { it.heroCode }.distinct()
        val rules = heroCodes.mapNotNull { code ->
            builderRepository.heroRules(code, locale)?.let { code to it }
        }.toMap()
        val identities = heroCodes.associateWith { builderRepository.identityTraits(it) }
        val signatures = rules.mapValues { (_, heroRules) ->
            heroRules.heroSetCode
                ?.let { cardDao.getCardSet(it, locale.code) }
                .orEmpty()
                .filter { it.code in heroRules.requiredCards }
                .associate { it.code to it.toDraftCard() }
        }
        DraftContext(
            pool = stock.pool,
            initialStock = stock.stock,
            rules = rules,
            identities = identities,
            signatureCards = signatures,
            poolAspectAvailable = (owned[DraftRules.POOL_PACK] ?: 0) > 0,
        )
    }

    /** True when the Deadpool pack is owned, so the identity page can offer 'Pool. */
    suspend fun poolAspectAvailable(): Boolean = withContext(ioDispatcher) {
        ownedPackDao.getOwned().any { it.packCode == DraftRules.POOL_PACK && it.quantity > 0 }
    }

    /**
     * Every printing of every player card, named in [locale] where a
     * translation exists and in English otherwise, as the shelf counts them.
     */
    private suspend fun stockRows(locale: CardLocale): List<StockRow> {
        val translated = cardDao.getPlayerCards(locale.code).associateBy { it.code }
        val fallback = cardDao.getPlayerCards(locale.fallback().code).associateBy { it.code }
        return (translated.keys + fallback.keys).map { code ->
            val card = translated[code] ?: fallback.getValue(code)
            StockRow(
                code = card.code,
                duplicateOfCode = card.duplicateOfCode?.takeIf { it.isNotBlank() },
                packCode = card.packCode,
                printedQuantity = card.quantity,
                card = card.toDraftCard(),
            )
        }
    }

    private fun CardEntity.toDraftCard() = DraftCard(
        canonicalCode = code,
        info = toDeckCardInfo(),
        condition = SynergyCondition.decode(synergyTraits),
        cost = cost,
        imageSrc = imageSrc,
        typeName = typeName,
        factionName = factionName,
    )

    // --- the end -------------------------------------------------------------------

    /** The names already in use on the device, which a default name must avoid. */
    suspend fun takenDeckNames(): List<String> = withContext(ioDispatcher) {
        deckRepository.getDecks().map { it.name }
    }

    /**
     * Saves every deck of the draft, or none.
     *
     * Each is put through the standard validation first. One that fails is
     * a bug in the engine, which promised legality at every pick: the state
     * is logged whole, the player is told, and nothing is written. The decks
     * that pass go through the ordinary local-deck path, which marks them
     * for sync like any other.
     */
    suspend fun finish(state: DraftState, context: DraftContext, locale: CardLocale): DraftOutcome =
        withContext(ioDispatcher) {
            val cards = context.cardInfo
            state.players.forEach { player ->
                val rules = context.rules[player.heroCode]
                    ?: return@withContext illegal(state, player.index, emptyList())
                val validation = DeckValidator.validate(rules, player.aspects, player.slots(), cards)
                if (!validation.isLegal) {
                    return@withContext illegal(state, player.index, validation.problems)
                }
            }

            val taken = takenDeckNames().toMutableList()
            val ids = state.players.map { player ->
                val rules = context.rules.getValue(player.heroCode!!)
                val name = player.deckName?.takeIf { it.isNotBlank() }
                    ?: DraftNaming.defaultName(player.heroName, player.aspects, rules, taken)
                taken += name
                deckRepository.createLocalDeck(
                    name = name,
                    heroCode = player.heroCode,
                    heroName = heroNameIn(player.heroCode, locale) ?: player.heroName,
                    aspects = player.aspects,
                    slots = player.slots(),
                )
            }
            sessionDao.clear()
            DraftOutcome.Saved(ids)
        }

    private fun illegal(state: DraftState, playerIndex: Int, problems: List<DeckProblem>): DraftOutcome {
        Log.e(
            TAG,
            "draft produced an illegal deck for player ${playerIndex + 1}: $problems\n" +
                json.encodeToString(DraftState.serializer(), state),
        )
        return DraftOutcome.Illegal(playerIndex, problems)
    }

    private suspend fun heroNameIn(heroCode: String, locale: CardLocale): String? =
        cardDao.getCardPreferringLocale(heroCode, locale.code)?.name

    private companion object {
        const val TAG = "DraftRepository"
    }
}
