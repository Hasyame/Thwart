package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * A card's condition on the identity playing it: Rocket Raccoon is playable
 * "only if your identity has the Guardian trait". The card is legal in any
 * deck; without the trait it is only ever a resource, which is what the
 * synergy warning is for.
 *
 * Spelled out in `docs/spec/synergie-et-draft.md`. The web client's
 * `scripts/lib/synergy.mjs` is the contract between the two, ported here
 * function for function, and `src/test/resources/synergy-fixture.json` is
 * its fixture, copied unchanged.
 */
data class SynergyCondition(
    /** Trait keys, any one of which satisfies the card. See [TraitKey]. */
    val anyOfTraits: List<String>,
    /**
     * True when the card reads "your hero has", which only the hero faces can
     * answer: an identity whose alter ego alone is Psionic does not qualify.
     */
    val heroOnly: Boolean = false,
) {
    /** True when [identity] can play the card. */
    fun compatibleWith(identity: IdentityTraits): Boolean {
        val faces = if (heroOnly) identity.heroFaces else identity.allFaces
        return anyOfTraits.any { it in faces }
    }

    /**
     * The stored form, kept in the `cards` table so SQL can filter on it:
     * `|guardian|`, `|x-force|x-men|`, `hero:|psionic|`. Every key is wrapped
     * in bars, so `LIKE '%|x-men|%'` cannot match `x-men-trainee`.
     */
    fun encode(): String = buildString {
        if (heroOnly) {
            append(HERO_PREFIX)
        }
        append(SEPARATOR)
        anyOfTraits.forEach { append(it).append(SEPARATOR) }
    }

    companion object {
        const val SEPARATOR = "|"
        const val HERO_PREFIX = "hero:"

        /**
         * The stored form of "no condition". Distinct from null, which means
         * the column has not been derived for this row yet.
         */
        const val NONE = ""

        fun decode(stored: String?): SynergyCondition? {
            if (stored.isNullOrEmpty()) {
                return null
            }
            val heroOnly = stored.startsWith(HERO_PREFIX)
            val keys = stored.removePrefix(HERO_PREFIX)
                .split(SEPARATOR)
                .filter { it.isNotEmpty() }
            return if (keys.isEmpty()) null else SynergyCondition(keys, heroOnly)
        }
    }
}

/**
 * The traits of an identity, face by face: the hero side(s) and the alter-ego
 * side(s), as keys. Ant-Man has two hero forms, Ironheart three versions; every
 * face counts, since the identity can turn to any of them.
 */
data class IdentityTraits(
    val heroFaces: Set<String>,
    val alterEgoFaces: Set<String>,
) {
    val allFaces: Set<String> get() = heroFaces + alterEgoFaces

    companion object {
        val NONE = IdentityTraits(emptySet(), emptySet())

        /** From the printed trait strings of each face. */
        fun of(heroTraits: List<String?>, alterEgoTraits: List<String?>) = IdentityTraits(
            heroFaces = heroTraits.flatMap { TraitKey.split(it) }.toSet(),
            alterEgoFaces = alterEgoTraits.flatMap { TraitKey.split(it) }.toSet(),
        )
    }
}

/** A card in the deck that its identity cannot play. Never a [DeckProblem]. */
data class SynergyWarning(val cardCode: String, val cardName: String)

/**
 * Reads the condition off a card's English text and judges a deck by it.
 *
 * Pure, and run once per card when the data is stored, never on display: the
 * result lives in `cards.synergyTraits`.
 */
object Synergy {

    /**
     * Whether "Play only if your hero has the [[X]] trait" is read as a
     * condition on the hero faces alone.
     *
     * Off, because the contract does not encode it yet: the web lists those
     * cards among the unrecognised, and turning this on here alone would
     * make the two clients disagree on Psi-Bow Attack. The model, the stored
     * form (`hero:|psionic|`) and [SynergyCondition.compatibleWith] are all
     * ready; when the web adds the field to `synergy.mjs` and the fixture,
     * this flips and both sides change together.
     */
    private const val HERO_FACE_RECOGNISED = false

    /**
     * The trait condition, in the forms the cards actually use, as the web
     * writes it: "your identity has the [[X]] trait", "you have the [[X]]
     * trait", any number of "[[Y]]" joined by "or" or "and", and the full
     * stop after "trait". The tag case varies from card to card
     * ([[guardian]], [[Avenger]], [[X-MEN]]), so it is normalised away.
     */
    private val TRAIT_CONDITION = Regex(
        buildString {
            append("""play only if (your identity has|you have""")
            if (HERO_FACE_RECOGNISED) {
                append("""|your hero has""")
            }
            append(""") the ((?:\[\[[^\]]+]](?:,? (?:or|and) )?)+) traits?\.""")
        },
        RegexOption.IGNORE_CASE,
    )

    private val TRAIT_TOKEN = Regex("""\[\[([^\]]+)]]""")

    /** Anything else that gates the card, reported so no case slips by. */
    private val ANY_CONDITION = Regex("""play only if[^\n]*""", RegexOption.IGNORE_CASE)

    /** The condition on the card, or null when it has none. */
    fun parse(realText: String?): SynergyCondition? {
        val match = TRAIT_CONDITION.find(realText ?: return null) ?: return null
        val keys = TRAIT_TOKEN.findAll(match.groupValues[2])
            .map { TraitKey.normalize(it.groupValues[1]) }
            .distinct()
            .toList()
        if (keys.isEmpty()) {
            return null
        }
        return SynergyCondition(
            anyOfTraits = keys,
            heroOnly = match.groupValues[1].equals("your hero has", ignoreCase = true),
        )
    }

    /**
     * The "Play only if" lines of a card whose condition is not one of the
     * recognised trait forms, for the census; empty when the card has no
     * such line or when every one was recognised. The rest of the line, not
     * the sentence: a full stop inside [[S.H.I.E.L.D.]] is not the end of one.
     */
    fun unrecognised(realText: String?): List<String> =
        ANY_CONDITION.findAll(realText ?: return emptyList())
            .map { it.value }
            .filterNot { TRAIT_CONDITION.containsMatchIn(it) }
            .map { it.trim() }
            .toList()

    /**
     * The cards of a deck that [identity] cannot play, in the order given.
     *
     * The hero's own cards are never judged: they are printed for this
     * identity and may name its trait as a matter of course.
     */
    fun warnings(
        identity: IdentityTraits,
        cards: List<SynergyCardInfo>,
    ): List<SynergyWarning> = cards
        .filter { !it.signature }
        .filter { card -> card.condition?.let { !it.compatibleWith(identity) } == true }
        .map { SynergyWarning(it.code, it.name) }
}

/** What the judge needs of a card in the deck. */
data class SynergyCardInfo(
    val code: String,
    val name: String,
    val condition: SynergyCondition?,
    /** True for the identity's own cards. */
    val signature: Boolean = false,
)
