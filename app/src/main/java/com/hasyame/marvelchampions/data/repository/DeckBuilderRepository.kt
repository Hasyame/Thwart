package com.hasyame.marvelchampions.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.deckbuilder.HeroDeckRulesParser
import com.hasyame.marvelchampions.data.deckbuilder.toDeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidation
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.deckbuilder.Synergy
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyWarning
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.CardQueryBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A hero that can be picked when starting a deck. */
data class HeroChoice(
    val card: CardEntity,
    val owned: Boolean,
)

@Singleton
class DeckBuilderRepository @Inject constructor(
    private val cardDao: CardDao,
    private val collectionRepository: CollectionRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun heroes(locale: CardLocale): List<HeroChoice> = withContext(ioDispatcher) {
        val owned = collectionRepository.getOwnedCodes()
        cardDao.getHeroes(locale.code).mapNotNull { summary ->
            // getHeroes returns one row per hero set; the identity card itself
            // is what carries the deck building rules.
            val card = cardDao.getCard(summary.code, locale.code)
                ?: findHeroCard(summary.code, locale)
            card?.let { HeroChoice(card = it, owned = it.packCode in owned) }
        }.sortedWith(compareByDescending<HeroChoice> { it.owned }.thenBy { it.card.name })
    }

    suspend fun heroRules(heroCode: String, locale: CardLocale): HeroDeckRules? =
        withContext(ioDispatcher) {
            val hero = cardDao.getCard(heroCode, locale.code) ?: return@withContext null

            // The identity's own cards, at the quantity printed on each.
            //
            // Everything in the hero's set except the three things that are not
            // deck cards: the identity, the alter-ego, and the obligation, which
            // is shuffled into the encounter deck instead. Adam Warlock's
            // Regeneration Cycle is an obligation, and requiring it made his
            // deck permanently illegal.
            //
            // Faction is deliberately not the filter. Spider-Woman's set holds
            // one event of each aspect — Venom Blast, Pheromones, Contaminant
            // Immunity, Inconspicuous — and those are her cards in every deck
            // she builds, whichever two aspects she picks.
            val signature = hero.cardSetCode
                ?.let { cardDao.getCardSet(it, locale.code) }
                .orEmpty()
                .filter { it.factionCode != ENCOUNTER_FACTION }
                .filter { it.typeCode != HERO_TYPE && it.typeCode != ALTER_EGO_TYPE }
                .associate { it.code to it.quantity }

            // The alter-ego's name is the identity's subtitle for the unique
            // rule, and it is on the other side of the card.
            val alterEgo = hero.linkedToCode?.let { cardDao.getCard(it, locale.code) }?.name

            HeroDeckRulesParser.parse(
                hero = hero,
                json = json,
                requiredCards = signature,
                alterEgoName = alterEgo,
            )
        }

    /** A card's name in [locale], or in the other language when untranslated. */
    suspend fun cardName(code: String, locale: CardLocale): String? = withContext(ioDispatcher) {
        cardDao.getCardPreferringLocale(code, locale.code)?.name
    }

    /**
     * The traits of every face of the identity, from the English rows, which
     * are what the trait keys are made of. Ant-Man's Giant and Tiny forms,
     * Ironheart's three versions: all of a set's hero and alter-ego cards.
     */
    suspend fun identityTraits(heroCode: String): IdentityTraits = withContext(ioDispatcher) {
        val hero = cardDao.getCardPreferringLocale(heroCode, CardLocale.ENGLISH.code)
            ?: return@withContext IdentityTraits.NONE
        val faces = hero.cardSetCode
            ?.let { cardDao.getCardSet(it, CardLocale.ENGLISH.code) }
            .orEmpty()
            .ifEmpty { listOf(hero) }
        IdentityTraits.of(
            heroTraits = faces.filter { it.typeCode == HERO_TYPE }.map { it.realTraits ?: it.traits },
            alterEgoTraits = faces.filter { it.typeCode == ALTER_EGO_TYPE }.map { it.realTraits ?: it.traits },
        )
    }

    /**
     * The cards of a deck its identity cannot play. Worked out each time it is
     * asked, from the deck as it stands, and never written anywhere.
     */
    suspend fun synergyWarnings(
        rules: HeroDeckRules,
        slots: Map<String, Int>,
        locale: CardLocale,
    ): List<SynergyWarning> = withContext(ioDispatcher) {
        val identity = identityTraits(rules.heroCode)
        val cards = slots.filterValues { it > 0 }.keys
            .mapNotNull { cardDao.getCardPreferringLocale(it, locale.code) }
            .sortedBy { it.name }
            .map { card ->
                SynergyCardInfo(
                    code = card.code,
                    name = card.name,
                    condition = SynergyCondition.decode(card.synergyTraits),
                    signature = rules.heroSetCode != null && card.cardSetCode == rules.heroSetCode,
                )
            }
        Synergy.warnings(identity, cards)
    }

    /**
     * Cards that can go in a deck: the hero's own signature cards, the chosen
     * aspects, and basic. Encounter and campaign cards are never player cards.
     */
    suspend fun candidateCards(
        heroSetCode: String?,
        aspects: List<String>,
        locale: CardLocale,
        query: String,
        ownedOnly: Boolean,
        /** When set, cards without synergy with this identity are left out. */
        synergyWith: IdentityTraits? = null,
        /** The editor's chips: a subset of the aspects and basic, or empty for all of them. */
        factions: Set<String> = emptySet(),
        typeCodes: Set<String> = emptySet(),
        /** A printed cost, or [COST_AND_ABOVE] and up. */
        cost: Int? = null,
        /** Whether the hero's own cards are listed too; off when a chip narrows the pool to an aspect. */
        includeHeroCards: Boolean = true,
        /** Whether the aspects and basic are listed; off when the hero chip alone is on. */
        includeAspectCards: Boolean = true,
    ): List<CardEntity> = withContext(ioDispatcher) {
        val allowed = (aspects + BASIC_FACTION).toSet()
        val chosen = factions.filter { it in allowed }.toSet().ifEmpty { allowed }
        val filter = CardFilter(
            query = query,
            factionCodes = chosen,
            typeCodes = typeCodes,
            minCost = cost,
            maxCost = cost?.takeIf { it < COST_AND_ABOVE },
            ownedOnly = ownedOnly,
            synergyWith = synergyWith,
        )
        val owned = if (ownedOnly) collectionRepository.getOwnedCodes() else emptySet()
        val built = CardQueryBuilder.build(filter, locale, owned, limit = CANDIDATE_LIMIT)
        val aspectCards = if (includeAspectCards) {
            cardDao.queryCards(SimpleSQLiteQuery(built.sql, built.args.toTypedArray()))
        } else {
            emptyList()
        }

        val heroCards = heroSetCode?.takeIf { includeHeroCards }?.let { setCode ->
            cardDao.getCardSet(setCode, locale.code)
                .filter { it.factionCode == HERO_FACTION && it.typeCode != HERO_TYPE }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .filter { typeCodes.isEmpty() || it.typeCode in typeCodes }
                .filter { cost == null || (it.cost != null && if (cost >= COST_AND_ABOVE) it.cost >= cost else it.cost == cost) }
        }.orEmpty()

        (heroCards + aspectCards).distinctBy { it.code }
    }

    /**
     * How many cards the deck may draw on in all, before any chip narrows
     * them: the aspects and basic, and the hero's own. The "N of M" under
     * the search box.
     */
    suspend fun poolSize(heroSetCode: String?, aspects: List<String>, locale: CardLocale, ownedOnly: Boolean): Int =
        withContext(ioDispatcher) {
            val owned = if (ownedOnly) collectionRepository.getOwnedCodes() else null
            val factions = (aspects + BASIC_FACTION).toSet()
            val filter = CardFilter(factionCodes = factions, ownedOnly = ownedOnly)
            val built = CardQueryBuilder.build(filter, locale, owned.orEmpty(), limit = Int.MAX_VALUE)
            val pool = cardDao.queryCards(SimpleSQLiteQuery(built.sql, built.args.toTypedArray())).size
            val heroCards = heroSetCode?.let { cardDao.getCardSet(it, locale.code) }
                .orEmpty()
                .count { it.factionCode == HERO_FACTION && it.typeCode != HERO_TYPE && (owned == null || it.packCode in owned) }
            pool + heroCards
        }

    /** The card types a player deck can hold, with their names in [locale], for the editor's chips. */
    suspend fun playerCardTypes(locale: CardLocale): List<Pair<String, String>> = withContext(ioDispatcher) {
        cardDao.distinctTypeNames(locale.code)
            .filter { it.code in PLAYER_TYPES }
            .sortedBy { PLAYER_TYPES.indexOf(it.code) }
            .map { it.code to it.name }
    }

    suspend fun validate(
        rules: HeroDeckRules,
        aspects: List<String>,
        slots: Map<String, Int>,
        locale: CardLocale,
    ): DeckValidation = withContext(ioDispatcher) {
        // The hero's own cards too, not only what is in the deck: a signature
        // card that is missing has to be named in the message, and a code is
        // not a name.
        val codes = slots.keys + rules.requiredCards.keys
        val cards = codes.mapNotNull { cardDao.getCard(it, locale.code) }
            .associate { it.code to it.toDeckCardInfo() }
        DeckValidator.validate(rules, aspects, slots, cards)
    }

    private suspend fun findHeroCard(setCode: String, locale: CardLocale): CardEntity? =
        cardDao.getCardSet(setCode, locale.code).firstOrNull { it.typeCode == HERO_TYPE }

    companion object {
        private const val BASIC_FACTION = "basic"
        private const val HERO_FACTION = "hero"
        private const val HERO_TYPE = "hero"
        private const val ALTER_EGO_TYPE = "alter_ego"
        private const val ENCOUNTER_FACTION = "encounter"
        // The pool is a few hundred cards and the list is lazy: no cap worth having.
        private const val CANDIDATE_LIMIT = 5_000

        /** The last cost chip stands for this cost and everything dearer. */
        const val COST_AND_ABOVE = 5

        /** In the order the chips are shown. */
        val PLAYER_TYPES = listOf("ally", "upgrade", "event", "resource", "support", "player_side_scheme")
    }
}
