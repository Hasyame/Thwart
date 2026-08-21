package com.hasyame.marvelchampions.domain.campaign.template

import kotlinx.serialization.Serializable

/**
 * A guard on a setup step, an effect or a branch.
 *
 * Every field present must hold, so `{"difficulty":"expert","flag":"x"}` means
 * both. Alternatives go through [any].
 *
 * Deliberately flat rather than a polymorphic hierarchy: the JSON stays close
 * to the sketch in the brief and readable by whoever is filling the template in
 * from the campaign book.
 */
@Serializable
data class Condition(
    /** Campaign difficulty, e.g. `expert`. */
    val difficulty: String? = null,

    /** A boolean answer from this scenario's questionnaire must be true. */
    val answer: String? = null,
    /** The same, negated. */
    val notAnswer: String? = null,

    /**
     * A yes/no answer given **for the hero being evaluated**.
     *
     * The difference from [answer] matters whenever a reward is per player: one
     * hero can earn it while another does not.
     */
    val heroAnswer: String? = null,
    val notHeroAnswer: String? = null,

    /**
     * A recorded card list must contain [contains], or hold at least [minSize]
     * cards.
     *
     * [minSize] is what keeps "remove the cards recorded in The Collection" off
     * the briefing when nothing was recorded: an instruction about an empty list
     * reads as though the app never consulted the log.
     */
    val cardList: String? = null,
    val contains: String? = null,
    /**
     * The list must *not* hold this card.
     *
     * NeXt Evolution offers the players a side scheme they have not chosen yet,
     * every scenario, out of a set that shrinks as they go. Expressing that as
     * "offer it unless it is already recorded" needs the negative form; there
     * is no flag to hang it on, because the record is the card itself.
     */
    val notContains: String? = null,
    val minSize: Int? = null,

    /** A flag must be set. `flagSet.scenarioId`, or just `flagSet`. */
    val flag: String? = null,
    val notFlag: String? = null,

    /** How many flags of a set are true, e.g. `countTrue >= 1`. */
    val countTrue: String? = null,
    val countAtLeast: Int? = null,
    val countAtMost: Int? = null,

    /** A counter comparison. Hero-scoped counters use the evaluating hero. */
    val counter: String? = null,
    val atLeast: Int? = null,
    val atMost: Int? = null,
    val equals: Int? = null,

    /** A choice answer must equal this option id. */
    val choice: String? = null,
    val choiceIs: String? = null,

    /**
     * True when at least one hero in the run satisfies the nested condition.
     *
     * Setup steps are shown once for the table, not per player, so a rule like
     * "deal this to whoever holds the Power Stone" needs to ask whether *some*
     * hero qualifies. Without it a hero-scoped [counter] would be read against
     * the campaign and always come back zero.
     */
    /**
     * True when a setup draw came up with a particular card, written
     * `drawId:cardCode`.
     *
     * A drawn card can carry rules of its own — Age of Apocalypse gives each
     * MISSION its own setup step and its own lasting consequence — and those
     * cannot be written into the scenario, because which one is in play is not
     * known until the app draws it.
     */
    val drawIs: String? = null,

    val anyHero: Condition? = null,

    /** True when any nested condition holds. */
    val any: List<Condition> = emptyList(),
    /** True when every nested condition holds. */
    val all: List<Condition> = emptyList(),
)

/**
 * An effect step.
 *
 * Arithmetic is deliberately kept out of the schema: a rule is several small
 * steps rather than one formula. [max] caps a single operation, [value] is a
 * literal and [from] takes the number the player answered.
 */
@Serializable
data class Effect(
    val op: String,
    @kotlinx.serialization.SerialName("when") val condition: Condition? = null,

    val counter: String? = null,
    val flag: String? = null,
    val cardList: String? = null,

    val value: Int? = null,
    val boolValue: Boolean? = null,
    /** Answer id supplying the value. */
    val from: String? = null,
    /**
     * Floor-divides the value before it is applied, for rules of the shape
     * "for every 2 of these, gain 1". Applied before [max] and [min].
     */
    val divideBy: Int? = null,
    /** Upper bound applied to this operation only. */
    val max: Int? = null,
    val min: Int? = null,

    val cardCode: String? = null,
    /** Applies per hero rather than once to the campaign. */
    val perHero: Boolean = false,
) {
    val operation: EffectOp
        get() = EffectOp.entries.firstOrNull { it.token == op.lowercase() } ?: EffectOp.UNKNOWN
}

enum class EffectOp(val token: String) {
    ADD_COUNTER("addcounter"),
    /**
     * Takes a value away from a counter, which is how "3 minus what was
     * recorded" is expressed: set it to 3, then subtract. Two small steps
     * rather than a formula in the schema.
     */
    SUBTRACT_COUNTER("subtractcounter"),
    SET_COUNTER("setcounter"),
    SET_HERO_COUNTER("setherocounter"),
    ADD_HERO_COUNTER("addherocounter"),
    SET_FLAG("setflag"),
    ADD_CARD("addcard"),
    ADD_CARDS_FROM_ANSWER("addcardsfromanswer"),
    /**
     * Replaces a card list with what was answered, rather than adding to it.
     *
     * Mutant Genesis records which Future Past cards are still in circulation
     * at the end of each scenario; the ones that reached the victory display
     * are gone for good. That is a list that shrinks, and adding to it would
     * shuffle a destroyed Sentinel back in next game.
     */
    SET_CARDS_FROM_ANSWER("setcardsfromanswer"),
    /**
     * Adds whatever a setup draw came up with to a card list, which is how a
     * randomly chosen card gets struck from the campaign log without asking the
     * player to tell the app what it already picked.
     */
    ADD_DRAWN_CARD("adddrawncard"),
    ELIMINATE_HERO("eliminatehero"),
    UNKNOWN("");

    companion object {
        fun isKnown(token: String): Boolean =
            entries.any { it.token == token.lowercase() && it != UNKNOWN }
    }
}
