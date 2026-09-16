package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A card in one language. Keyed by `(code, locale)` so the English and French
 * versions coexist as separate rows.
 *
 * Every field MarvelCDB returns is stored. Structured fields (`meta`,
 * `deck_options`, `deck_requirements`, `duplicated_by`) are kept as raw JSON
 * strings: nothing in the app interprets them yet, and losing them would be
 * worse than storing them opaquely.
 */
@Entity(
    tableName = "cards",
    primaryKeys = ["code", "locale"],
    indices = [
        Index("locale"),
        Index("packCode"),
        Index("typeCode"),
        Index("factionCode"),
        Index("cardSetCode"),
        Index("linkedToCode"),
        Index("duplicateOfCode"),
    ],
)
data class CardEntity(
    val code: String,
    val locale: String,

    // Identity
    val name: String,
    val realName: String,
    val subname: String? = null,
    val position: Int,
    val quantity: Int,
    val url: String? = null,
    val octgnId: String? = null,

    // Pack and set
    val packCode: String,
    val packName: String,
    val packLegacy: Boolean,
    val packWave: Int? = null,
    val cardSetCode: String? = null,
    val cardSetName: String? = null,
    val cardSetTypeNameCode: String? = null,
    val cardSetParentCode: String? = null,
    val setPosition: Int? = null,

    // Classification
    val typeCode: String,
    val typeName: String,
    val factionCode: String,
    val factionName: String,
    val traits: String? = null,
    val realTraits: String? = null,

    // Text
    val text: String? = null,
    val realText: String? = null,
    val flavor: String? = null,
    val errata: String? = null,
    val illustrator: String? = null,
    val backText: String? = null,
    val backName: String? = null,
    val backFlavor: String? = null,

    // Images
    val imageSrc: String? = null,
    val backImageSrc: String? = null,

    // Cost and resources
    val cost: Int? = null,
    val costPerHero: Boolean = false,
    val costStar: Boolean = false,
    val resourcePhysical: Int? = null,
    val resourceMental: Int? = null,
    val resourceEnergy: Int? = null,
    val resourceWild: Int? = null,

    // Hero and ally statistics
    val health: Int? = null,
    val healthPerGroup: Boolean = false,
    val healthPerHero: Boolean = false,
    val healthStar: Boolean = false,
    val handSize: Int? = null,
    val attack: Int? = null,
    val attackCost: Int? = null,
    val attackStar: Boolean = false,
    val thwart: Int? = null,
    val thwartCost: Int? = null,
    val thwartStar: Boolean = false,
    val defense: Int? = null,
    val defenseStar: Boolean = false,
    val recover: Int? = null,
    val recoverStar: Boolean = false,

    // Encounter side
    val stage: String? = null,
    val boost: Int? = null,
    val boostStar: Boolean = false,
    val scheme: Int? = null,
    val schemeStar: Boolean = false,
    val schemeAcceleration: Int? = null,
    val schemeCrisis: Int? = null,
    val schemeHazard: Int? = null,
    val schemeAmplify: Int? = null,
    val threat: Int? = null,
    val threatFixed: Boolean = false,
    val threatPerGroup: Boolean = false,
    val threatStar: Boolean = false,
    val baseThreat: Int? = null,
    val baseThreatFixed: Boolean = false,
    val baseThreatPerGroup: Boolean = false,
    val escalationThreat: Int? = null,
    val escalationThreatFixed: Boolean = false,
    val escalationThreatStar: Boolean = false,

    // Deck building — raw JSON, uninterpreted
    val deckLimit: Int? = null,
    val deckRequirementsJson: String? = null,
    val deckOptionsJson: String? = null,
    val restrictionsJson: String? = null,

    // Relationships
    val linkedToCode: String? = null,
    val linkedToName: String? = null,
    val duplicateOfCode: String? = null,
    val duplicateOfName: String? = null,
    val duplicatedByJson: String? = null,

    // Flags and miscellany
    val isUnique: Boolean = false,
    val hidden: Boolean = false,
    val permanent: Boolean = false,
    val doubleSided: Boolean = false,
    val spoiler: Int? = null,
    val metaJson: String? = null,

    // Search — folded at write time by SearchNormalizer. These are what
    // cards_fts indexes; see docs/ARCHITECTURE.md.
    @ColumnInfo(name = "searchName") val searchName: String,
    @ColumnInfo(name = "searchText") val searchText: String,
    @ColumnInfo(name = "searchTraits") val searchTraits: String,

    /**
     * The card's condition on the identity playing it, derived from the
     * English text when the row is written: `|guardian|`, `hero:|psionic|`,
     * `""` for a card with no condition. Null only on a row written before
     * the column existed, which the start-up pass fills in. See
     * [com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition].
     */
    val synergyTraits: String? = null,
)
