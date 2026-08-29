package com.hasyame.marvelchampions.domain.randomizer

/** The four difficulty levels the game offers. */
/**
 * A difficulty, and the pack whose cards it needs.
 *
 * Difficulty is not an app setting: each level is a physical set of encounter
 * cards, and it arrived in a particular box. Standard I and Expert I are in the
 * Core Set, Standard II and Expert II came with The Hood, Standard III with The
 * Age of Apocalypse. The randomiser offered all five to everybody, which is a
 * draw somebody cannot set up.
 *
 * MarvelCDB has no concept of this — it knows cards, not which difficulty they
 * constitute — so the mapping is stated here. It changes about once every two
 * years.
 */
enum class Difficulty(val packCode: String) {
    STANDARD_I("core"),
    STANDARD_II("hood"),
    STANDARD_III("aoa"),
    EXPERT_I("core"),
    EXPERT_II("hood"),
}

/** A field of the draw. Each one can be locked and rerolled on its own. */
enum class DrawField {
    SCENARIO,
    DIFFICULTY,
    MODULAR_SETS,
    PLAYER_COUNT,
    HEROES,
    ASPECTS,
}

/** A card set the randomiser can pick, named by the card database at runtime. */
data class SetRef(
    val code: String,
    val packCode: String,
)

data class HeroRef(
    val code: String,
    val packCode: String,
)

/**
 * What a scenario requires. Generated into `assets/scenario_rules.json` by
 * `tools/generate-scenario-rules.mjs`.
 *
 * [mandatoryModulars] must always be used. [recommendedModulars] are only a
 * printed suggestion, so they stay in the random pool rather than being forced.
 */
data class ScenarioRule(
    val code: String,
    val packCode: String,
    val modularCount: Int,
    val mandatoryModulars: List<String> = emptyList(),
    val recommendedModulars: List<String> = emptyList(),
    /** The generator could not parse this scenario with confidence. */
    val needsReview: Boolean = false,
    /**
     * Upper bound when a scenario draws a variable number of sets.
     *
     * Civil War takes three or four, decided at the table. Everything else
     * takes exactly [modularCount], so this defaults to it.
     */
    val modularCountMax: Int = modularCount,
    /**
     * Packs its modular sets may come from. Empty means anything owned.
     *
     * Civil War and She-Hulk share a pool that is legal only in their own
     * games, and the sets in it must not turn up anywhere else.
     */
    val modularPacks: List<String> = emptyList(),
)

/** Everything the user owns, already filtered to owned packs. */
data class RandomizerPools(
    val scenarios: List<SetRef> = emptyList(),
    val modularSets: List<SetRef> = emptyList(),
    val heroes: List<HeroRef> = emptyList(),
    val aspects: List<String> = emptyList(),
    /**
     * Difficulties the collection can field, which is a property of the packs
     * owned rather than a preference.
     */
    val difficulties: List<Difficulty> = Difficulty.entries,
)

data class RandomizerFilters(
    val excludedScenarios: Set<String> = emptySet(),
    val excludedHeroes: Set<String> = emptySet(),
    val excludedAspects: Set<String> = emptySet(),
    /**
     * Modular sets the player cannot field.
     *
     * Owning a pack is not the same as owning every modular set in it — a
     * second-hand box, a proxy build, a set lent out. The draw has no way to
     * know, so the player says.
     */
    val excludedModularSets: Set<String> = emptySet(),
    val allowedDifficulties: Set<Difficulty> = Difficulty.entries.toSet(),
    /**
     * Whether the draw may also hand out difficulty sets beyond the one it
     * picked, which is how a table asks for a harder game than the box.
     *
     * Off, because it is not what the game means by a difficulty: Standard II
     * and Expert II are extra encounter cards you choose to shuffle in, and a
     * randomiser that did it unasked would be setting up a game nobody agreed
     * to.
     */
    val includeExtraDifficulty: Boolean = false,
    val minPlayers: Int = 1,
    val maxPlayers: Int = 4,
)

/** One hero with the aspect they are playing. */
data class HeroAssignment(
    val heroCode: String,
    val aspect: String,
)

/**
 * A complete draw. Fields are nullable because a pool can be empty — an
 * unowned collection must produce a partial result the UI can explain, not a
 * crash.
 */
data class RandomizerDraw(
    val scenarioCode: String? = null,
    val difficulty: Difficulty? = null,
    /** Difficulty sets shuffled in on top of [difficulty]. Usually empty. */
    val extraDifficulties: List<Difficulty> = emptyList(),
    val modularSetCodes: List<String> = emptyList(),
    val playerCount: Int = 1,
    val heroes: List<HeroAssignment> = emptyList(),
    /** Modular sets forced by the scenario, a subset of [modularSetCodes]. */
    val mandatoryModularCodes: List<String> = emptyList(),
) {
    val isComplete: Boolean
        get() = scenarioCode != null && difficulty != null && heroes.isNotEmpty()
}
