package com.hasyame.marvelchampions.data.marvelcdb.dto

import kotlinx.serialization.Serializable

/**
 * `assets/scenario_rules.json`, produced by
 * `tools/generate-scenario-rules.mjs`.
 *
 * Codes only — scenario and modular set names come from the card database at
 * runtime, already in the user's chosen language.
 */
@Serializable
data class ScenarioRulesFileDto(
    val schemaVersion: Int,
    val note: String? = null,
    val generatedFrom: String? = null,
    val scenarios: List<ScenarioRuleDto>,
    val modularSets: List<ModularSetDto> = emptyList(),
)

@Serializable
data class ScenarioRuleDto(
    val code: String,
    val packCode: String,
    val modularCount: Int,
    val mandatoryModulars: List<String> = emptyList(),
    val recommendedModulars: List<String> = emptyList(),
    val needsReview: Boolean = false,
    /** Packs the modular sets must come from, when the scenario says so. */
    val modularPacks: List<String> = emptyList(),
    /** Extra modular sets per player, on top of [modularCount]. */
    val modularCountPerHero: Int = 0,
)

@Serializable
data class ModularSetDto(
    val code: String,
    val packCode: String,
)
