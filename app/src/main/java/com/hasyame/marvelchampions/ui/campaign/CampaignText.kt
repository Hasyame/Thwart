package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hasyame.marvelchampions.data.repository.CampaignRun

/**
 * Words a campaign gives a specific meaning to, which the printed material sets
 * apart from ordinary prose.
 *
 * MISSION and OVERSEER are not descriptions in Age of Apocalypse: a MISSION side
 * scheme cannot be thwarted and an OVERSEER minion sits outside any player's
 * control. Reading them as ordinary words loses that, so they are set in bold
 * italic the way the campaign sets them.
 */
private val KEYWORDS = listOf(
    "MISSION", "OVERSEER", "PRELATE",
    // The two faces of a Fear No Evil environment. These are the words a
    // table scans a setup step for — which card, and which way up — so they
    // are set apart wherever they appear, not only on the pressure board.
    "ACHEVÉ", "ÉCHOUÉ", "ACHIEVED", "FAILED",
)

// Bounded by lookarounds rather than by `\b`, and deliberately not by
// the `(?U)` flag: that is a JVM inline flag, Android matches with ICU,
// and an unknown flag throws as the pattern compiles. Being a top-level
// val, that landed the first time any campaign text was drawn — the
// moment a scenario was chosen — while the JVM-run tests stayed green.
/** The pattern itself, so a test can assert what it is made of. */
internal val KEYWORD_PATTERN_FOR_TEST: Regex get() = KEYWORD_PATTERN

private val KEYWORD_PATTERN = Regex(
    KEYWORDS.joinToString("|") { """(?<!\p{L})$it(?!\p{L})""" },
)

private val KEYWORD_STYLE = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
)

/** Marks up the campaign's own keywords wherever they appear in [text]. */
fun campaignText(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in KEYWORD_PATTERN.findAll(text)) {
        append(text.substring(index, match.range.first))
        withStyleSpan(match.value)
        index = match.range.last + 1
    }
    append(text.substring(index))
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleSpan(value: String) {
    pushStyle(KEYWORD_STYLE)
    append(value)
    pop()
}

/**
 * Fills `{drawId}` in a template string with the card the app drew.
 *
 * A question like "was the MISSION defeated?" is answerable but vague — there
 * are five of them and the app chose one. Naming it means the player is
 * confirming the thing actually on their table rather than translating from a
 * generic question.
 */
fun resolveDraws(text: String, run: CampaignRun, scenarioId: String?): String =
    // Both braces escaped. Android's regex engine is ICU, which rejects a bare
    // closing brace as a syntax error where the JVM quietly accepts it — so
    // this compiled fine in tests and threw on the device.
    Regex("""\{(card:)?([A-Za-z0-9_]+)\}""").replace(text) { match ->
        val (cardPrefix, name) = match.destructured
        if (cardPrefix.isNotEmpty()) {
            // A card code, resolved through the card database so the name comes
            // out in the reader's language. Writing the name into the template
            // instead left English prose sitting beside a French card chip.
            run.names.card(name).quoted()
        } else {
            val drawn = run.state.draws[scenarioId].orEmpty()[name].orEmpty()
            if (drawn.isEmpty()) {
                // Nothing drawn: drop the placeholder rather than print braces.
                ""
            } else {
                drawn.joinToString(", ") { run.names.card(it).quoted() }
            }
        }
    }.replace("  ", " ").trim()

/**
 * A card name as it appears mid-sentence, in quotes.
 *
 * A setup step is read while hunting through a pile of cards, and a title
 * running into the prose around it is genuinely hard to pick out — worse for
 * the ones that are ordinary words, like an attachment called Passe-Partout.
 * Quoting is done here rather than in the templates so every campaign gets it
 * and no template can forget.
 */
private fun String.quoted(): String = if (isBlank()) this else "\"" + this + "\""
