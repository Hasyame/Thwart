package com.hasyame.marvelchampions.data.deckbuilder

import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.deckbuilder.DeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.DeckOption
import com.hasyame.marvelchampions.domain.deckbuilder.HERO_DECK_SIZE_OVERRIDES
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds a hero's deck building rules from the raw JSON stored on the card.
 *
 * `deck_requirements` and `deck_options` are kept as opaque JSON in the
 * database because almost nothing uses them: only two heroes carry
 * requirements and five carry options. Parsing lazily here keeps that
 * exception out of the schema.
 */
object HeroDeckRulesParser {

    fun parse(
        hero: CardEntity,
        json: Json,
        /** The hero's own cards and their printed quantities, excluding the identity. */
        requiredCards: Map<String, Int> = emptyMap(),
        /** The alter-ego side's name, which serves as the identity's subtitle. */
        alterEgoName: String? = null,
    ): HeroDeckRules {
        val requirements = hero.deckRequirementsJson?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        val requirement = requirements?.jsonArray?.firstOrNull()?.jsonObject

        val aspectCount = requirement?.get("aspects")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

        // Adam Warlock's `{"aspects": 4, "limit": 1}`. The limit is on copies of
        // a card, not on cards per aspect: read the other way it made a legal
        // forty-card deck illegal, because one card from each of four aspects
        // is a four-card deck.
        val copyLimitOverride = requirement?.get("limit")?.jsonPrimitive?.content?.toIntOrNull()

        val options = hero.deckOptionsJson
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            ?.jsonArray
            ?.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                DeckOption(
                    traits = obj.stringList("trait"),
                    types = obj.stringList("type"),
                    resources = obj.stringList("resource"),
                    limit = obj["limit"]?.jsonPrimitive?.content?.toIntOrNull(),
                )
            }
            .orEmpty()

        // Deck size is not in the card data at all, so any hero that departs
        // from 40-50 has to be listed in the curated override map.
        val sizeOverride = HERO_DECK_SIZE_OVERRIDES[hero.code]

        return HeroDeckRules(
            heroCode = hero.code,
            heroSetCode = hero.cardSetCode,
            aspectCount = aspectCount,
            copyLimitOverride = copyLimitOverride,
            // Picking more than one aspect always means picking them in equal
            // number; no hero picks several and is free to weight them.
            aspectsMustBalance = aspectCount > 1,
            requiredCards = requiredCards,
            identityTitle = hero.name,
            identityAlterEgo = alterEgoName,
            options = options,
            minDeckSize = sizeOverride?.first,
            maxDeckSize = sizeOverride?.second,
        )
    }

    private fun kotlinx.serialization.json.JsonObject.stringList(key: String): List<String> =
        this[key]?.let { element ->
            runCatching { element.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
        }.orEmpty()
}

fun CardEntity.toDeckCardInfo(): DeckCardInfo = DeckCardInfo(
    code = code,
    name = name,
    subtitle = subname,
    factionCode = factionCode,
    typeCode = typeCode,
    cardSetCode = cardSetCode,
    traits = traits,
    deckLimit = deckLimit,
    isUnique = isUnique,
    resourcePhysical = resourcePhysical,
    resourceMental = resourceMental,
    resourceEnergy = resourceEnergy,
    resourceWild = resourceWild,
)
