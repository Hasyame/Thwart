package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.DeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.DeckOption
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition

/** A small card pool with every kind of card the engine has to reason about. */
object DraftFixtures {

    const val SPIDER_MAN = "01001a"
    const val ADAM_WARLOCK = "21031a"
    const val SPIDER_WOMAN = "05001a"
    const val MARIA_HILL = "50001a"

    val ASPECTS = listOf("aggression", "justice", "leadership", "protection")

    /** Five signature cards, one copy each, so 40 cards means 35 picks. */
    private fun signature(prefix: String): Map<String, Int> = (1..5).associate { "$prefix$it" to 1 }

    val spiderMan = HeroDeckRules(
        heroCode = SPIDER_MAN,
        heroSetCode = "spider_man",
        requiredCards = signature("sm"),
        identityTitle = "Spider-Man",
        identityAlterEgo = "Peter Parker",
    )

    val adamWarlock = HeroDeckRules(
        heroCode = ADAM_WARLOCK,
        heroSetCode = "adam_warlock",
        aspectCount = 4,
        copyLimitOverride = 1,
        aspectsMustBalance = true,
        requiredCards = signature("aw"),
        identityTitle = "Adam Warlock",
        identityAlterEgo = "Adam Warlock",
    )

    val spiderWoman = HeroDeckRules(
        heroCode = SPIDER_WOMAN,
        heroSetCode = "spider_woman",
        aspectCount = 2,
        aspectsMustBalance = true,
        requiredCards = signature("sw"),
        identityTitle = "Spider-Woman",
        identityAlterEgo = "Jessica Drew",
    )

    val mariaHill = HeroDeckRules(
        heroCode = MARIA_HILL,
        heroSetCode = "maria_hill",
        requiredCards = signature("mh"),
        identityTitle = "Maria Hill",
        identityAlterEgo = "Maria Hill",
        options = listOf(DeckOption(traits = listOf("S.H.I.E.L.D."), types = listOf("support"), limit = 3)),
    )

    val rules = mapOf(
        SPIDER_MAN to spiderMan,
        ADAM_WARLOCK to adamWarlock,
        SPIDER_WOMAN to spiderWoman,
        MARIA_HILL to mariaHill,
    )

    val identities = mapOf(
        SPIDER_MAN to IdentityTraits(setOf("avenger"), setOf("genius")),
        ADAM_WARLOCK to IdentityTraits(setOf("guardian", "mystic"), setOf("mystic")),
        SPIDER_WOMAN to IdentityTraits(setOf("avenger", "spy"), setOf("shield")),
        MARIA_HILL to IdentityTraits(setOf("shield", "spy"), setOf("shield", "spy")),
    )

    fun card(
        code: String,
        faction: String,
        name: String = code,
        type: String = "event",
        limit: Int? = 3,
        unique: Boolean = false,
        traits: String? = null,
        condition: SynergyCondition? = null,
    ) = DraftCard(
        canonicalCode = code,
        info = DeckCardInfo(
            code = code,
            name = name,
            factionCode = faction,
            typeCode = type,
            cardSetCode = null,
            traits = traits,
            deckLimit = limit,
            isUnique = unique,
        ),
        condition = condition,
        cost = 1,
        imageSrc = null,
        typeName = type,
        factionName = faction,
    )

    /**
     * Fifteen plain cards per aspect, ten basics, and a few special ones:
     * a Guardian-only ally, a unique ally named after Spider-Man, a
     * one-copy card, two S.H.I.E.L.D. supports in Leadership.
     */
    fun pool(): Map<String, DraftCard> {
        val cards = mutableListOf<DraftCard>()
        ASPECTS.forEach { aspect ->
            (1..15).forEach { cards += card("${aspect.take(3)}$it", aspect) }
        }
        (1..10).forEach { cards += card("bas$it", "basic") }
        cards += card("rocket", "basic", name = "Rocket Raccoon", type = "ally", limit = 1, unique = true, condition = SynergyCondition(listOf("guardian")))
        cards += card("spidey", "basic", name = "Spider-Man", type = "ally", limit = 1, unique = true)
        cards += card("single", "justice", name = "One Copy Only", limit = 1)
        cards += card("shield1", "leadership", name = "Helicarrier", type = "support", traits = "S.H.I.E.L.D. Location.")
        cards += card("shield2", "leadership", name = "Quinjet", type = "support", traits = "S.H.I.E.L.D. Vehicle.")
        return cards.associateBy { it.canonicalCode }
    }

    fun signatureInfo(rules: HeroDeckRules): Map<String, DraftCard> =
        rules.requiredCards.keys.associateWith { code ->
            card(code, "hero", type = "event", limit = 1).let { it.copy(info = it.info.copy(cardSetCode = rules.heroSetCode)) }
        }

    fun context(pool: Map<String, DraftCard> = pool(), poolAspect: Boolean = false) = DraftContext(
        pool = pool,
        initialStock = stock(pool),
        rules = rules,
        identities = identities,
        signatureCards = rules.mapValues { signatureInfo(it.value) },
        poolAspectAvailable = poolAspect,
    )

    /** Three copies of everything, whatever the card's own limit. */
    fun stock(pool: Map<String, DraftCard>, copies: Int = 3): Map<String, Int> = pool.keys.associateWith { copies }

    fun player(index: Int, rules: HeroDeckRules, aspects: List<String>, deckSize: Int = 40) = DraftPlayer(
        index = index,
        heroCode = rules.heroCode,
        heroName = rules.identityTitle!!,
        heroSetCode = rules.heroSetCode,
        aspects = aspects,
        deckSize = deckSize,
        signature = rules.requiredCards,
    )

    fun state(
        vararg players: DraftPlayer,
        settings: DraftSettings = DraftSettings(players = players.size),
        pool: Map<String, DraftCard> = pool(),
        copies: Int = 3,
        seed: Long = 42L,
    ) = DraftState(
        settings = settings,
        players = players.toList(),
        phase = DraftPhase.IDENTITY,
        stock = stock(pool, copies),
        seed = seed,
    )
}
