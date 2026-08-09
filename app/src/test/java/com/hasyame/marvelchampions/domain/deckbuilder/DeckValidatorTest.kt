package com.hasyame.marvelchampions.domain.deckbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckValidatorTest {

    private val heroRules = HeroDeckRules(
        heroCode = "01001a",
        heroSetCode = "spider_man",
        aspectCount = 1,
    )

    private fun card(
        code: String,
        faction: String,
        type: String = "ally",
        setCode: String? = null,
        deckLimit: Int? = 3,
        unique: Boolean = false,
        traits: String? = null,
        energy: Int? = null,
    ) = DeckCardInfo(
        code = code,
        name = "Card $code",
        factionCode = faction,
        typeCode = type,
        cardSetCode = setCode,
        traits = traits,
        deckLimit = deckLimit,
        isUnique = unique,
        resourceEnergy = energy,
    )

    /** A legal filler deck of the given size, all from one aspect. */
    private fun fillerDeck(count: Int = MINIMUM_DECK_SIZE, faction: String = "justice") =
        (1..count).associate { "j$it" to 1 } to
            (1..count).associate { "j$it" to card("j$it", faction) }

    @Test
    fun `a deck of the minimum size in one aspect is legal`() {
        val (slots, cards) = fillerDeck()

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(result.problems.toString(), result.isLegal)
        assertEquals(MINIMUM_DECK_SIZE, result.totalCards)
    }

    @Test
    fun `too few cards is reported with both numbers`() {
        val (slots, cards) = fillerDeck(count = 10)

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(
            result.problems.any { it == DeckProblem.TooFewCards(10, MINIMUM_DECK_SIZE) },
        )
    }

    @Test
    fun `a deck at the maximum size is legal`() {
        val (slots, cards) = fillerDeck(count = MAXIMUM_DECK_SIZE)

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `too many cards is reported`() {
        val (slots, cards) = fillerDeck(count = MAXIMUM_DECK_SIZE + 1)

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(
            result.problems.any {
                it == DeckProblem.TooManyCards(MAXIMUM_DECK_SIZE + 1, MAXIMUM_DECK_SIZE)
            },
        )
    }

    @Test
    fun `a hero override replaces both bounds`() {
        // No such hero is known, but the mechanism has to work the day one
        // appears, without touching the validator.
        val oddHero = heroRules.copy(minDeckSize = 25, maxDeckSize = 30)
        val (slots, cards) = fillerDeck(count = 28)

        assertTrue(DeckValidator.validate(oddHero, listOf("justice"), slots, cards).isLegal)

        val (tooMany, tooManyCards) = fillerDeck(count = 31)
        assertTrue(
            DeckValidator.validate(oddHero, listOf("justice"), tooMany, tooManyCards)
                .problems.any { it is DeckProblem.TooManyCards },
        )
    }

    @Test
    fun `a card from another aspect is rejected`() {
        val (slots, cards) = fillerDeck()
        val withIntruder = slots + ("agg1" to 1)
        val allCards = cards + ("agg1" to card("agg1", "aggression"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withIntruder, allCards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OffAspectCard && it.cardCode == "agg1"
            },
        )
    }

    @Test
    fun `basic cards are always allowed`() {
        val (slots, cards) = fillerDeck()
        val withBasic = slots + ("b1" to 1)
        val allCards = cards + ("b1" to card("b1", "basic"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withBasic, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `the hero's own cards are allowed`() {
        val (slots, cards) = fillerDeck()
        val withSignature = slots + ("h1" to 1)
        val allCards = cards + ("h1" to card("h1", "hero", setCode = "spider_man"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withSignature, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `another hero's signature card is rejected`() {
        // Faction alone is not enough: hero-faction cards belong to one hero.
        val (slots, cards) = fillerDeck()
        val withForeign = slots + ("x1" to 1)
        val allCards = cards + ("x1" to card("x1", "hero", setCode = "iron_man"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withForeign, allCards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OffAspectCard && it.cardCode == "x1"
            },
        )
    }

    @Test
    fun `exceeding the copy limit is reported`() {
        val (slots, cards) = fillerDeck(count = MINIMUM_DECK_SIZE)
        val over = slots + ("j1" to 4)

        val result = DeckValidator.validate(heroRules, listOf("justice"), over, cards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OverCopyLimit && it.cardCode == "j1" && it.limit == 3
            },
        )
    }

    @Test
    fun `a duplicated unique card is reported`() {
        val (slots, cards) = fillerDeck()
        val withUnique = slots + ("u1" to 2)
        val allCards = cards + (
            "u1" to card("u1", "justice", deckLimit = 1, unique = true)
            )

        val result = DeckValidator.validate(heroRules, listOf("justice"), withUnique, allCards)

        assertTrue(result.problems.any { it is DeckProblem.DuplicateUniqueCard })
    }

    @Test
    fun `the wrong number of aspects is reported`() {
        val (slots, cards) = fillerDeck()

        val result = DeckValidator.validate(heroRules, listOf("justice", "aggression"), slots, cards)

        assertTrue(result.problems.any { it == DeckProblem.WrongAspectCount(2, 1) })
    }

    @Test
    fun `spider-woman needs exactly two aspects`() {
        // The only two heroes with deck_requirements in the data are
        // Spider-Woman (2 aspects) and Adam Warlock (4, one card each).
        val spiderWoman = heroRules.copy(heroSetCode = "spider_woman", aspectCount = 2)
        val (slots, cards) = fillerDeck()

        assertFalse(
            DeckValidator.validate(spiderWoman, listOf("justice"), slots, cards).isLegal,
        )
        assertTrue(
            DeckValidator.validate(
                spiderWoman,
                listOf("justice", "aggression"),
                slots,
                cards,
            ).problems.none { it is DeckProblem.WrongAspectCount },
        )
    }

    /**
     * Adam Warlock: four aspects in equal number, and a single copy of anything
     * that is not his.
     *
     * MarvelCDB states this as `{"aspects": 4, "limit": 1}`, and the limit was
     * read as a cap per aspect — which made a legal forty-card deck illegal,
     * because one card from each of four aspects is a four-card deck.
     */
    private val warlock = HeroDeckRules(
        heroCode = "21031a",
        heroSetCode = "adam_warlock",
        aspectCount = 4,
        copyLimitOverride = 1,
        aspectsMustBalance = true,
    )

    private val fourAspects = listOf("justice", "aggression", "leadership", "protection")

    /** Ten single copies from each of the four aspects: forty cards, balanced. */
    private fun warlockDeck(): Pair<Map<String, Int>, Map<String, DeckCardInfo>> {
        val slots = mutableMapOf<String, Int>()
        val cards = mutableMapOf<String, DeckCardInfo>()
        fourAspects.forEach { aspect ->
            repeat(10) { index ->
                val code = "$aspect$index"
                slots[code] = 1
                cards[code] = card(code, aspect)
            }
        }
        return slots to cards
    }

    @Test
    fun `adam warlock's forty card deck is legal`() {
        val (slots, cards) = warlockDeck()

        val result = DeckValidator.validate(warlock, fourAspects, slots, cards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `adam warlock may not take a second copy of anything`() {
        val (slots, cards) = warlockDeck()
        // A second copy of one card, and one fewer elsewhere so the deck still
        // has forty cards and the aspects still balance.
        val doubled = slots.toMutableMap().apply {
            this["justice0"] = 2
            remove("justice9")
        }

        val result = DeckValidator.validate(warlock, fourAspects, doubled, cards)

        assertTrue(
            result.problems.toString(),
            result.problems.any { it is DeckProblem.OverCopyLimit && it.limit == 1 },
        )
    }

    @Test
    fun `adam warlock's aspects must be equal`() {
        val (slots, cards) = warlockDeck()
        val lopsided = slots.toMutableMap().apply {
            remove("justice9")
            this["aggression10"] = 1
        }
        val withExtra = cards + ("aggression10" to card("aggression10", "aggression"))

        val result = DeckValidator.validate(warlock, fourAspects, lopsided, withExtra)

        val unbalanced = result.problems.filterIsInstance<DeckProblem.UnbalancedAspects>()
        assertEquals(1, unbalanced.size)
        assertEquals(9, unbalanced.single().counts["justice"])
        assertEquals(11, unbalanced.single().counts["aggression"])
    }

    @Test
    fun `spider-woman's two aspects must be equal`() {
        val jessica = HeroDeckRules(
            heroCode = "04031a",
            heroSetCode = "spider_woman",
            aspectCount = 2,
            aspectsMustBalance = true,
        )
        val slots = mutableMapOf<String, Int>()
        val cards = mutableMapOf<String, DeckCardInfo>()
        repeat(21) { slots["j$it"] = 1; cards["j$it"] = card("j$it", "justice") }
        repeat(19) { slots["a$it"] = 1; cards["a$it"] = card("a$it", "aggression") }

        val result = DeckValidator.validate(jessica, listOf("justice", "aggression"), slots, cards)

        assertTrue(
            result.problems.toString(),
            result.problems.any { it is DeckProblem.UnbalancedAspects },
        )
    }

    @Test
    fun `three copies means three by title, not three of each printing`() {
        // 225 player-card titles are printed under more than one code. Counting
        // per code let a deck hold three of one printing and three of another.
        val (slots, cards) = fillerDeck(count = 37)
        val withReprints = slots + mapOf("r1" to 2, "r2" to 2)
        val reprintCards = cards + mapOf(
            "r1" to card("r1", "justice").copy(name = "Reprinted"),
            "r2" to card("r2", "justice").copy(name = "Reprinted"),
        )

        val result = DeckValidator.validate(heroRules, listOf("justice"), withReprints, reprintCards)

        assertTrue(
            result.problems.toString(),
            result.problems.any {
                it is DeckProblem.OverCopyLimit && it.cardName == "Reprinted" && it.quantity == 4
            },
        )
    }

    @Test
    fun `the identity card counts against its own unique title`() {
        // Peter Parker cannot take the Spider-Man ally who is also Peter
        // Parker. The identity has no subtitle of its own, so the alter-ego
        // stands in for one.
        val peter = heroRules.copy(
            identityTitle = "Spider-Man",
            identityAlterEgo = "Peter Parker",
        )
        val (slots, cards) = fillerDeck(count = 39)
        val withAlly = slots + mapOf("ally" to 1)
        val allyCards = cards + mapOf(
            "ally" to card("ally", "justice", unique = true)
                .copy(name = "Spider-Man", subtitle = "Peter Parker"),
        )

        val result = DeckValidator.validate(peter, listOf("justice"), withAlly, allyCards)

        assertTrue(
            result.problems.toString(),
            result.problems.any { it is DeckProblem.DuplicateUniqueCard },
        )
    }

    @Test
    fun `a unique card of the same title but another subtitle may share the deck`() {
        // Miles Morales is a different Spider-Man, and legal in Peter's deck.
        val peter = heroRules.copy(
            identityTitle = "Spider-Man",
            identityAlterEgo = "Peter Parker",
        )
        val (slots, cards) = fillerDeck(count = 39)
        val withAlly = slots + mapOf("ally" to 1)
        val allyCards = cards + mapOf(
            "ally" to card("ally", "justice", unique = true)
                .copy(name = "Spider-Man", subtitle = "Miles Morales"),
        )

        val result = DeckValidator.validate(peter, listOf("justice"), withAlly, allyCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `the hero's own cards must all be there, in their printed numbers`() {
        val withSignature = heroRules.copy(
            requiredCards = mapOf("01003" to 2, "01005" to 3),
        )
        val (slots, cards) = fillerDeck(count = 37)
        // Two of the three Swinging Web Kicks, and no Backflips at all.
        val deck = slots + mapOf("01005" to 2)
        val info = cards + mapOf(
            "01003" to card("01003", "hero", setCode = "spider_man").copy(name = "Backflip"),
            "01005" to card("01005", "hero", setCode = "spider_man")
                .copy(name = "Swinging Web Kick"),
        )

        val result = DeckValidator.validate(withSignature, listOf("justice"), deck, info)

        val missing = result.problems.filterIsInstance<DeckProblem.MissingRequiredCard>()
        assertEquals(2, missing.size)
        assertTrue(missing.any { it.cardName == "Backflip" && it.actual == 0 && it.required == 2 })
        assertTrue(
            missing.any { it.cardName == "Swinging Web Kick" && it.actual == 2 && it.required == 3 },
        )
    }

    @Test
    fun `a unique card with no subtitle is the character itself`() {
        // The "Captain America" title upgrade carries no subtitle, so it is the
        // same Captain America the deck is built around and cannot be in it.
        // Half the name clashes in the pool are of this shape.
        val steve = heroRules.copy(
            identityTitle = "Captain America",
            identityAlterEgo = "Steve Rogers",
        )
        val (slots, cards) = fillerDeck(count = 39)
        val withTitle = slots + mapOf("53023" to 1)
        val titleCards = cards + mapOf(
            "53023" to card("53023", "leadership", type = "upgrade", unique = true)
                .copy(name = "Captain America", subtitle = null),
        )

        val result = DeckValidator.validate(steve, listOf("justice"), withTitle, titleCards)

        assertTrue(
            result.problems.toString(),
            result.problems.any {
                it is DeckProblem.DuplicateUniqueCard && it.cardCode == "53023"
            },
        )
    }

    @Test
    fun `a hero's own aspect cards are legal whatever aspects were chosen`() {
        // Spider-Woman's set holds one event of each aspect. Venom Blast is an
        // aggression card and belongs in her deck even when she has picked
        // justice and leadership.
        val jessica = HeroDeckRules(
            heroCode = "04031a",
            heroSetCode = "spider_woman",
            aspectCount = 2,
            aspectsMustBalance = true,
            requiredCards = mapOf("04035" to 2),
        )
        val slots = mutableMapOf<String, Int>("04035" to 2)
        val cards = mutableMapOf(
            "04035" to card("04035", "aggression", type = "event", setCode = "spider_woman"),
        )
        repeat(19) { slots["j$it"] = 1; cards["j$it"] = card("j$it", "justice") }
        repeat(19) { slots["l$it"] = 1; cards["l$it"] = card("l$it", "leadership") }

        val result = DeckValidator.validate(jessica, listOf("justice", "leadership"), slots, cards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `another hero's signature card is still refused`() {
        val (slots, cards) = fillerDeck(count = 39)
        val withStranger = slots + mapOf("thor1" to 1)
        val strangerCards = cards + mapOf(
            "thor1" to card("thor1", "hero", setCode = "thor"),
        )

        val result = DeckValidator.validate(heroRules, listOf("justice"), withStranger, strangerCards)

        assertTrue(
            result.problems.toString(),
            result.problems.any { it is DeckProblem.OffAspectCard && it.cardCode == "thor1" },
        )
    }

    @Test
    fun `a hero's own cards are not held to the three copy limit`() {
        // Some signature cards are printed in threes and one in four; the
        // printed quantity is the rule for them.
        val withSignature = heroRules.copy(requiredCards = mapOf("sig" to 4))
        val (slots, cards) = fillerDeck(count = 36)
        val deck = slots + mapOf("sig" to 4)
        val info = cards + mapOf(
            "sig" to card("sig", "hero", setCode = "spider_man", deckLimit = 4),
        )

        val result = DeckValidator.validate(withSignature, listOf("justice"), deck, info)

        assertTrue(
            result.problems.toString(),
            result.problems.none { it is DeckProblem.OverCopyLimit },
        )
    }

    @Test
    fun `a deck option admits an otherwise off-aspect card`() {
        // Cyclops: {"trait":["x-men"],"type":["ally"]} — X-Men allies of any
        // aspect are legal for him.
        val cyclops = heroRules.copy(
            heroSetCode = "cyclops",
            options = listOf(DeckOption(traits = listOf("x-men"), types = listOf("ally"))),
        )
        val (slots, cards) = fillerDeck()
        val withXMen = slots + ("x1" to 1)
        val allCards = cards + (
            "x1" to card("x1", "aggression", type = "ally", traits = "X-Men.")
            )

        val result = DeckValidator.validate(cyclops, listOf("justice"), withXMen, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `a deck option does not admit a card that fails one of its criteria`() {
        val cyclops = heroRules.copy(
            heroSetCode = "cyclops",
            options = listOf(DeckOption(traits = listOf("x-men"), types = listOf("ally"))),
        )
        val (slots, cards) = fillerDeck()
        // Right trait, wrong type.
        val withEvent = slots + ("x1" to 1)
        val allCards = cards + (
            "x1" to card("x1", "aggression", type = "event", traits = "X-Men.")
            )

        val result = DeckValidator.validate(cyclops, listOf("justice"), withEvent, allCards)

        assertTrue(result.problems.any { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `a limited deck option stops admitting once it is full`() {
        // Gamora: {"limit":6,"trait":["attack","thwart"],"type":["event"]}
        val gamora = heroRules.copy(
            heroSetCode = "gamora",
            options = listOf(
                DeckOption(traits = listOf("attack", "thwart"), types = listOf("event"), limit = 6),
            ),
        )
        val (slots, cards) = fillerDeck()
        val withSeven = slots + ("g1" to 7)
        val allCards = cards + (
            "g1" to card("g1", "aggression", type = "event", traits = "Attack.", deckLimit = 9)
            )

        val result = DeckValidator.validate(gamora, listOf("justice"), withSeven, allCards)

        assertTrue(result.problems.any { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `a resource based deck option matches on the printed resource`() {
        // Wonder Man: {"resource":["energy"],"type":["event"]}
        val wonderMan = heroRules.copy(
            heroSetCode = "wonder_man",
            options = listOf(DeckOption(resources = listOf("energy"), types = listOf("event"))),
        )
        val (slots, cards) = fillerDeck()
        val withEnergyEvent = slots + ("w1" to 1)
        val allCards = cards + (
            "w1" to card("w1", "aggression", type = "event", energy = 1)
            )

        val result = DeckValidator.validate(wonderMan, listOf("justice"), withEnergyEvent, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `a card missing from the database is skipped rather than rejected`() {
        // Happens when a deck references a pack newer than the last card sync.
        val (slots, cards) = fillerDeck()
        val withUnknown = slots + ("unknown" to 2)

        val result = DeckValidator.validate(heroRules, listOf("justice"), withUnknown, cards)

        assertTrue(result.problems.none { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `an empty deck reports too few cards and nothing else`() {
        val result = DeckValidator.validate(heroRules, listOf("justice"), emptyMap(), emptyMap())

        assertEquals(1, result.problems.size)
        assertTrue(result.problems.single() is DeckProblem.TooFewCards)
    }

    @Test
    fun `no hero currently overrides the deck size`() {
        // Deck size appears nowhere in the card data, so the override map is
        // curated. It stays empty until a real exception turns up.
        assertTrue(HERO_DECK_SIZE_OVERRIDES.isEmpty())
    }
}
