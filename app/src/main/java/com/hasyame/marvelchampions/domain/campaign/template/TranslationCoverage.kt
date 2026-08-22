package com.hasyame.marvelchampions.domain.campaign.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * How much of a campaign's own text exists in each language.
 *
 * Counted, not estimated. [LocalizedText] falls back (`en ?: fr`), so a
 * campaign with no English half still reads, in French, to an English player —
 * which is honest only if the app says so. Fear No Evil is the case in point:
 * it was written from the French box and its English is still being finished.
 *
 * This measures the campaign's own writing, not the cards. A pack whose cards
 * MarvelCDB has not translated shows English card names whatever this says, and
 * the campaigns where that happens explain it in their notice.
 */
data class TranslationCoverage(
    val frenchPercent: Int,
    val englishPercent: Int,
) {
    /** True when there is nothing to point out, so the line can be hidden. */
    val isComplete: Boolean get() = frenchPercent >= 100 && englishPercent >= 100
}

/**
 * Walks the serialised template rather than its fields.
 *
 * Reaching every [LocalizedText] by hand means naming every place one can
 * appear, and the next one added would be missed silently. The JSON has them
 * all by construction.
 */
fun CampaignTemplate.translationCoverage(json: Json = CoverageJson): TranslationCoverage {
    val texts = mutableListOf<JsonObject>()
    collectLocalizedText(json.encodeToJsonElement(this), texts)

    // A step that carries no text in either language is not untranslated: some
    // exist only to make the app draw a card. Counting them would drag every
    // campaign below 100% for no reason a reader could act on.
    val written = texts.filter { it.half("fr") != null || it.half("en") != null }
    if (written.isEmpty()) {
        return TranslationCoverage(100, 100)
    }
    return TranslationCoverage(
        frenchPercent = percent(written.count { it.half("fr") != null }, written.size),
        englishPercent = percent(written.count { it.half("en") != null }, written.size),
    )
}

private val CoverageJson = Json { encodeDefaults = true }

private fun JsonObject.half(language: String): String? =
    (this[language] as? JsonPrimitive)?.contentOrNullIfBlank()

private fun JsonPrimitive.contentOrNullIfBlank(): String? =
    if (isString && content.isNotBlank()) content else null

/** A LocalizedText is an object with fr and/or en holding plain strings. */
private fun collectLocalizedText(element: JsonElement, into: MutableList<JsonObject>) {
    when (element) {
        is JsonObject -> {
            val looksLocalized = ("fr" in element || "en" in element) &&
                element.values.all { it is JsonPrimitive }
            if (looksLocalized) {
                into.add(element)
            }
            element.values.forEach { collectLocalizedText(it, into) }
        }
        is JsonArray -> element.forEach { collectLocalizedText(it, into) }
        else -> Unit
    }
}

/**
 * Rounds towards the truthful answer at the edges.
 *
 * A campaign one string short of complete must not read 100%, and one string in
 * must not read 0%, because those are exactly the two claims a reader would act
 * on.
 */
private fun percent(have: Int, total: Int): Int {
    if (total == 0) return 100
    if (have == total) return 100
    if (have == 0) return 0
    return (have * 100.0 / total).toInt().coerceIn(1, 99)
}
