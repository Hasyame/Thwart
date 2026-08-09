package com.hasyame.marvelchampions.domain.campaign

/**
 * The setup printed on a scenario's first main scheme card.
 *
 * Every scenario tells you how to set itself up, and that text is on the 1A
 * side of the main scheme — put this into play, attach that, discard so many
 * cards. The app was showing which cards to gather and the campaign's own extra
 * steps, and leaving the player to pick the card out of the box to read the
 * part in between.
 *
 * It comes out of the card database rather than out of a template, which means
 * it arrives in whatever language the cards are in and is never a copy of
 * Fantasy Flight's text living in this repository.
 *
 * Only stage 1A carries one. Later stages are what happens when the scheme
 * advances, and asking them for a setup correctly gets nothing back.
 */
object SchemeSetup {

    /**
     * The heading, which is bold, and inconsistently so.
     *
     * Nebula writes `<b>Setup</b>:`, Hela `<b> Setup: </b>` with the colon
     * inside the tag and spaces around it, and the French endpoint
     * `<b>Mise en place</b> :` with the space before the colon that French
     * typography wants. All three are the same heading.
     */
    private val heading = Regex(
        """<b>\s*(?:Setup|Mise en place)\s*:?\s*</b>\s*:?\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Whatever heading comes after it — usually none, since Setup is last. */
    private val nextHeading = Regex("""<b>\s*[^<]{2,40}\s*</b>\s*:""")

    /**
     * A full stop that ends a step.
     *
     * Not every full stop does. One after a single capital is an initial, and
     * "Agents of S.H.I.E.L.D." would otherwise become five steps. One before an
     * opening bracket introduces an aside — Mysterio's "(Shuffle.)" belongs to
     * the sentence it follows, not to a step of its own.
     */
    private val stepBreak = Regex("""(?<![A-Z])\.\s+(?=[A-ZÀ-Ý“"])""")

    private val trait = Regex("""\[\[([^\]]+)]]""")
    private val icon = Regex("""\[([a-z_]+)]""")
    private val tag = Regex("""<[^>]+>""")
    private val spaceBeforePunctuation = Regex("""\s+([.,;:])""")
    private val runOfSpaces = Regex("""\s{2,}""")

    /**
     * The setup steps printed on [text], or empty when it carries none.
     *
     * Two scenarios genuinely have none: Ebony Maw and Thanos put theirs in the
     * rules insert instead. Empty is the right answer there, not a guess.
     */
    fun steps(text: String?): List<String> {
        val body = section(text) ?: return emptyList()
        return stepBreak.split(body)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.endsWithSentence()) it else "$it." }
    }

    private fun section(text: String?): String? {
        val match = heading.find(text.orEmpty()) ?: return null
        val rest = text!!.substring(match.range.last + 1)
        val end = nextHeading.find(rest)?.range?.first ?: rest.length
        return readable(rest.substring(0, end)).ifBlank { null }
    }

    private fun readable(raw: String): String = raw
        // A trait reads as a trait on the printed card, which is small capitals
        // the app has no font for; capitals are the honest approximation.
        .replace(trait) { it.groupValues[1].uppercase() }
        // "2[per_hero] cards" has to become "2 per player cards" — the rules
        // reference calls this icon PER PLAYER, so the app does too.
        .replace("[per_hero]", " per player")
        .replace(icon) { " " + it.groupValues[1].replace('_', ' ').uppercase() }
        .replace(tag, "")
        .replace(runOfSpaces, " ")
        .replace(spaceBeforePunctuation, "$1")
        .trim()

    private fun String.endsWithSentence(): Boolean =
        endsWith(".") || endsWith("!") || endsWith("?") ||
            endsWith(".)") || endsWith(".\"")
}
