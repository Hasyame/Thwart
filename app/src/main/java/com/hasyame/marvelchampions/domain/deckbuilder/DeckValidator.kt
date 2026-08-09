package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * Checks a deck against the rules that are actually knowable.
 *
 * Pure, so every rule is testable without a database.
 *
 * Deliberately *not* checked: anything MarvelCDB does not encode. The validator
 * would rather stay silent than invent a rule and reject a legal deck.
 */
object DeckValidator {

    private const val BASIC_FACTION = "basic"
    private const val HERO_FACTION = "hero"

    /** The printed limit on an ordinary card, when the data does not say. */
    private const val MAXIMUM_COPIES = 3

    fun validate(
        rules: HeroDeckRules,
        chosenAspects: List<String>,
        /** Card code to quantity, hero card excluded. */
        slots: Map<String, Int>,
        cards: Map<String, DeckCardInfo>,
    ): DeckValidation {
        val problems = mutableListOf<DeckProblem>()
        val totalCards = slots.values.sum()

        if (chosenAspects.size != rules.aspectCount) {
            problems += DeckProblem.WrongAspectCount(chosenAspects.size, rules.aspectCount)
        }

        if (totalCards < rules.effectiveMinimum) {
            problems += DeckProblem.TooFewCards(totalCards, rules.effectiveMinimum)
        }
        if (totalCards > rules.effectiveMaximum) {
            problems += DeckProblem.TooManyCards(totalCards, rules.effectiveMaximum)
        }

        // The hero's own cards are not optional and not adjustable: the deck
        // must hold every one of them, in the number printed on the card.
        rules.requiredCards.forEach { (code, required) ->
            val actual = slots[code] ?: 0
            if (actual != required) {
                problems += DeckProblem.MissingRequiredCard(
                    cardCode = code,
                    cardName = cards[code]?.name ?: code,
                    required = required,
                    actual = actual,
                )
            }
        }

        problems += copyLimitProblems(rules, slots, cards)
        problems += uniqueProblems(rules, slots, cards)

        // How many cards each deck_options allowance has admitted so far, so a
        // limited allowance stops admitting once it is full.
        val optionUsage = IntArray(rules.options.size)
        val aspectUsage = mutableMapOf<String, Int>()

        for ((code, quantity) in slots) {
            val card = cards[code] ?: continue

            if (quantity < 1) {
                continue
            }

            when {
                // The hero's own cards, whatever faction they carry, and never
                // counted towards a chosen aspect. Spider-Woman's set holds one
                // event of each aspect: they are hers in every deck, and
                // reading them as aspect cards both made two of them illegal
                // and threw off the balance between her two chosen aspects.
                rules.heroSetCode != null && card.cardSetCode == rules.heroSetCode -> Unit

                // A hero-faction card from somebody else's set, which is never
                // legal — Spider-Man's Web-Shooter cannot go in Thor's deck.
                card.factionCode == HERO_FACTION ->
                    problems += DeckProblem.OffAspectCard(code, card.name, card.factionCode)

                card.factionCode == BASIC_FACTION -> Unit

                card.factionCode in chosenAspects ->
                    aspectUsage[card.factionCode] =
                        (aspectUsage[card.factionCode] ?: 0) + quantity

                else -> {
                    val admitted = admitByOption(rules, card, quantity, optionUsage)
                    if (!admitted) {
                        problems += DeckProblem.OffAspectCard(code, card.name, card.factionCode)
                    }
                }
            }
        }

        // A hero who picks more than one aspect has to take the same number from
        // each: four for Adam Warlock, two for Spider-Woman. Only checked once
        // the right number of aspects has been chosen, because complaining that
        // one aspect and no other are unequal helps nobody.
        if (rules.aspectsMustBalance && chosenAspects.size == rules.aspectCount) {
            val counts = chosenAspects.associateWith { aspectUsage[it] ?: 0 }
            if (counts.values.distinct().size > 1) {
                problems += DeckProblem.UnbalancedAspects(counts)
            }
        }

        return DeckValidation(problems = problems, totalCards = totalCards)
    }

    /**
     * Copy limits, counted by title rather than by code.
     *
     * "No more than three copies by title" is not the same as three copies of a
     * card code: 225 player-card titles in the pool are printed under more than
     * one code, so three of one printing and three of another is six copies of
     * the same card and was passing.
     *
     * The hero's own signature cards are exempt — their printed quantity is the
     * rule for them, and it is checked separately.
     */
    private fun copyLimitProblems(
        rules: HeroDeckRules,
        slots: Map<String, Int>,
        cards: Map<String, DeckCardInfo>,
    ): List<DeckProblem> {
        val problems = mutableListOf<DeckProblem>()
        val byTitle = slots.entries
            .filter { it.value > 0 && it.key !in rules.requiredCards }
            .mapNotNull { (code, quantity) -> cards[code]?.let { it to quantity } }
            .groupBy { (card, _) -> card.name }

        byTitle.forEach { (title, entries) ->
            val total = entries.sumOf { it.second }
            val first = entries.first().first
            if (first.isUnique) {
                return@forEach
            }
            // The identity's own limit wins where it has one: Adam Warlock
            // allows a single copy of anything that is not his.
            val limit = rules.copyLimitOverride
                ?: entries.minOf { (card, _) -> card.deckLimit ?: MAXIMUM_COPIES }
            if (total > limit) {
                problems += DeckProblem.OverCopyLimit(first.code, title, total, limit)
            }
        }
        return problems
    }

    /**
     * One copy of each unique card, counting the identity card as one of them.
     *
     * Keyed on title *and* subtitle, because that is what the rule turns on:
     * Spider-Man (Miles Morales) and Spider-Man (Peter Parker) are two people
     * and may share a deck. The identity has no subtitle of its own, so its
     * alter-ego stands in — which is exactly why Peter Parker cannot take the
     * Spider-Man ally that is also Peter Parker, while Miles is fine.
     */
    private fun uniqueProblems(
        rules: HeroDeckRules,
        slots: Map<String, Int>,
        cards: Map<String, DeckCardInfo>,
    ): List<DeckProblem> {
        val problems = mutableListOf<DeckProblem>()
        val counted = mutableMapOf<Pair<String, String>, Int>()

        rules.identityTitle?.let { title ->
            counted[title to rules.identityAlterEgo.orEmpty()] = 1
        }

        slots.entries
            .filter { it.value > 0 }
            .mapNotNull { (code, quantity) -> cards[code]?.let { Triple(code, it, quantity) } }
            .filter { (_, card, _) -> card.isUnique }
            .forEach { (code, card, quantity) ->
                val key = card.name to card.subtitle.orEmpty()
                val total = (counted[key] ?: 0) + quantity
                counted[key] = total
                if (total > 1) {
                    problems += DeckProblem.DuplicateUniqueCard(code, card.name, total)
                }
            }
        return problems
    }

    /**
     * Tries to admit an off-aspect card through one of the hero's
     * `deck_options` allowances, consuming capacity from the first that fits.
     */
    private fun admitByOption(
        rules: HeroDeckRules,
        card: DeckCardInfo,
        quantity: Int,
        usage: IntArray,
    ): Boolean {
        rules.options.forEachIndexed { index, option ->
            if (!option.matches(card)) {
                return@forEachIndexed
            }
            val limit = option.limit
            if (limit == null) {
                return true
            }
            if (usage[index] + quantity <= limit) {
                usage[index] += quantity
                return true
            }
        }
        return false
    }

    private fun DeckOption.matches(card: DeckCardInfo): Boolean {
        if (types.isNotEmpty() && card.typeCode !in types) {
            return false
        }
        if (traits.isNotEmpty() && traits.none { card.hasTrait(it) }) {
            return false
        }
        if (resources.isNotEmpty() && resources.none { card.hasResource(it) }) {
            return false
        }
        // An allowance with no criteria at all would admit everything, which is
        // never what the data means.
        return types.isNotEmpty() || traits.isNotEmpty() || resources.isNotEmpty()
    }
}
