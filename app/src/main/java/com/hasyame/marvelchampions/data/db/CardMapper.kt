package com.hasyame.marvelchampions.data.db

import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.domain.deckbuilder.Synergy
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.serialization.json.JsonElement

/**
 * Maps the API shape onto the stored shape.
 *
 * The `search*` columns are folded here rather than at query time so the FTS
 * index contains accent-free lowercase text. Both name fields are indexed
 * together, which is what lets a French user find a card by its English name.
 */
fun CardDto.toEntity(locale: CardLocale): CardEntity = CardEntity(
    code = code,
    locale = locale.code,
    name = name,
    realName = realName,
    subname = subname,
    position = position,
    quantity = quantity,
    url = url,
    octgnId = octgnId,
    packCode = packCode,
    packName = packName,
    packLegacy = packLegacy,
    packWave = packWave,
    cardSetCode = cardSetCode,
    cardSetName = cardSetName,
    cardSetTypeNameCode = cardSetTypeNameCode,
    cardSetParentCode = cardSetParentCode,
    setPosition = setPosition,
    typeCode = typeCode,
    typeName = typeName,
    factionCode = factionCode,
    factionName = factionName,
    traits = traits,
    realTraits = realTraits,
    text = text,
    realText = realText,
    flavor = flavor,
    errata = errata,
    illustrator = illustrator,
    backText = backText,
    backName = backName,
    backFlavor = backFlavor,
    imageSrc = imagesrc,
    backImageSrc = backimagesrc,
    cost = cost,
    costPerHero = costPerHero,
    costStar = costStar,
    resourcePhysical = resourcePhysical,
    resourceMental = resourceMental,
    resourceEnergy = resourceEnergy,
    resourceWild = resourceWild,
    health = health,
    healthPerGroup = healthPerGroup,
    healthPerHero = healthPerHero,
    healthStar = healthStar,
    handSize = handSize,
    attack = attack,
    attackCost = attackCost,
    attackStar = attackStar,
    thwart = thwart,
    thwartCost = thwartCost,
    thwartStar = thwartStar,
    defense = defense,
    defenseStar = defenseStar,
    recover = recover,
    recoverStar = recoverStar,
    stage = stage,
    boost = boost,
    boostStar = boostStar,
    scheme = scheme,
    schemeStar = schemeStar,
    schemeAcceleration = schemeAcceleration,
    schemeCrisis = schemeCrisis,
    schemeHazard = schemeHazard,
    schemeAmplify = schemeAmplify,
    threat = threat,
    threatFixed = threatFixed,
    threatPerGroup = threatPerGroup,
    threatStar = threatStar,
    baseThreat = baseThreat,
    baseThreatFixed = baseThreatFixed,
    baseThreatPerGroup = baseThreatPerGroup,
    escalationThreat = escalationThreat,
    escalationThreatFixed = escalationThreatFixed,
    escalationThreatStar = escalationThreatStar,
    deckLimit = deckLimit,
    deckRequirementsJson = deckRequirements.asJsonString(),
    deckOptionsJson = deckOptions.asJsonString(),
    restrictionsJson = restrictions.asJsonString(),
    linkedToCode = linkedToCode,
    linkedToName = linkedToName,
    duplicateOfCode = duplicateOfCode,
    duplicateOfName = duplicateOfName,
    duplicatedByJson = duplicatedBy?.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]"),
    isUnique = isUnique,
    hidden = hidden,
    permanent = permanent,
    doubleSided = doubleSided,
    spoiler = spoiler,
    metaJson = meta.asJsonString(),
    searchName = SearchNormalizer.normalize(
        listOfNotNull(name, realName.takeIf { it != name }, subname).joinToString(" "),
    ),
    searchText = SearchNormalizer.normalize(
        listOfNotNull(text, flavor, backText).joinToString(" "),
    ),
    searchTraits = SearchNormalizer.normalize(traits),
    synergyTraits = deriveSynergy(realText, text),
)

/**
 * The English text is the one read, on every locale's row: MarvelCDB serves
 * `real_text` in English beside the translation, and the French wording of
 * the same condition varies more than the English does.
 */
fun deriveSynergy(realText: String?, text: String?): String =
    Synergy.parse(realText ?: text)?.encode() ?: SynergyCondition.NONE

private fun JsonElement?.asJsonString(): String? = this?.toString()
