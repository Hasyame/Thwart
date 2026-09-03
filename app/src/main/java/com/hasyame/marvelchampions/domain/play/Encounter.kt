package com.hasyame.marvelchampions.domain.play

import kotlinx.serialization.Serializable

/**
 * One printed side of a villain or a main scheme.
 *
 * The numbers are as printed on the card, not as they end up on the table:
 * most of them are "per player", and the scaling happens here rather than in
 * the card data because the card does not know how many people turned up.
 *
 * The card database expresses that two different ways and they are not
 * consistent — a villain's health carries `health_per_hero`, where true means
 * multiply, while a scheme's threat carries `threat_fixed`, where **false**
 * means multiply. Both are normalised to [perPlayer] on the way in, once, so
 * that nothing downstream has to remember which spelling it is dealing with.
 */
data class EncounterSide(
    val name: String,
    val stage: String,
    /** Villain health, or a main scheme's threat limit. Null when [starred]. */
    val value: Int?,
    val perPlayer: Boolean,
    /**
     * The card prints a star rather than a number, so the scenario decides it.
     * Five cards do this — Juggernaut and Mojo among them — and no amount of
     * card data will say what the number is, so the player types it.
     */
    val starred: Boolean = false,
    /** Threat already on a scheme when it comes into play. Schemes only. */
    val startingThreat: Int = 0,
    val startingThreatPerPlayer: Boolean = false,
    /**
     * Threat the campaign puts on the scheme on top of what the card prints.
     *
     * Already worked out, and not scaled again: Fear No Evil starts a job with
     * a threat for every pressure box ticked against it, and that is a flat
     * amount whatever the printed threat beside it does.
     */
    val extraStartingThreat: Int = 0,
    /** Threat added at the end of every round. Schemes only. */
    val escalation: Int = 0,
    val escalationPerPlayer: Boolean = false,
) {
    fun totalFor(players: Int): Int? = value?.timesPlayers(perPlayer, players)

    fun startingThreatFor(players: Int): Int =
        startingThreat.timesPlayers(startingThreatPerPlayer, players) + extraStartingThreat

    fun escalationFor(players: Int): Int = escalation.timesPlayers(escalationPerPlayer, players)

    private fun Int.timesPlayers(scales: Boolean, players: Int): Int =
        if (scales) this * players else this
}

/** The villain and the main scheme of one scenario, in printed order. */
data class EncounterSetup(
    val villain: List<EncounterSide> = emptyList(),
    val scheme: List<EncounterSide> = emptyList(),
    val players: Int = 1,
    /**
     * How many copies of the main scheme are on the table at once.
     *
     * One almost everywhere: a table plays a main scheme and turns it over.
     * Fear No Evil's racket job deals one to *each* player, who works their own
     * market on their own, so three players have three schemes running side by
     * side and finishing at different times. Folding those into a single bar
     * would count to a limit nobody is playing to, and could not say whose
     * scheme was nearly done.
     */
    val schemeCopies: Int = 1,
) {
    /** Nothing to count is not worth showing. */
    val isUsable: Boolean get() = villain.isNotEmpty() || scheme.isNotEmpty()
}

/**
 * Where the counters stand. Separate from the scenario, which cannot change.
 *
 * Serialisable because a game put down mid-play has to be able to write these
 * numbers somewhere and find them again. They cannot be re-derived: the damage
 * on the villain and the threat on the scheme are the game, not a function of
 * it.
 */
@Serializable
data class EncounterProgress(
    val villainIndex: Int = 0,
    val damage: Int = 0,
    val schemeIndex: Int = 0,
    val threat: Int = 0,
    /**
     * Threat on the second and later copies of the main scheme.
     *
     * The first copy's threat stays in [threat] rather than all of them moving
     * into one list. A game put away before this field existed wrote `threat`
     * into its saved counters, and renaming it would have every paused game
     * come back with its scheme empty — the exact loss the saved counters were
     * added to stop.
     */
    val extraThreats: List<Int> = emptyList(),
    val round: Int = 1,
    /** Filled in by the player, for the stages that print a star. */
    val manualVillainHealth: Int? = null,
    val manualSchemeLimit: Int? = null,
)

/**
 * A game being counted: the scenario's printed numbers, and where the table
 * has got to.
 *
 * Immutable — every move returns a new one — so the whole thing can live in UI
 * state and be compared, restored or thrown away without anything being able
 * to mutate it from underneath.
 *
 * Deliberately only counters. It does not know that Ultron drones enter play
 * or that a Crisis icon stops thwarting: a tracker that half-adjudicates rules
 * is one that is wrong at somebody's table, and then the numbers it *is*
 * keeping stop being trusted either.
 */
data class Encounter(
    val setup: EncounterSetup = EncounterSetup(),
    val progress: EncounterProgress = EncounterProgress(),
) {

    val villainSide: EncounterSide? get() = setup.villain.getOrNull(progress.villainIndex)

    val schemeSide: EncounterSide? get() = setup.scheme.getOrNull(progress.schemeIndex)

    /** The villain's health at this stage, or what the player typed for a star. */
    val villainHealth: Int?
        get() = villainSide?.totalFor(setup.players) ?: progress.manualVillainHealth

    /** The threat this scheme advances at, or what the player typed for a star. */
    val schemeLimit: Int?
        get() = schemeSide?.totalFor(setup.players) ?: progress.manualSchemeLimit

    val villainDefeated: Boolean get() = villainHealth?.let { progress.damage >= it } == true

    /** Copies of the main scheme in play, never fewer than one. */
    val schemeCopies: Int get() = setup.schemeCopies.coerceAtLeast(1)

    /** Threat on one copy of the main scheme. Copy zero is the table's own. */
    fun threatOn(copyIndex: Int): Int =
        if (copyIndex <= 0) progress.threat else progress.extraThreats.getOrElse(copyIndex - 1) { 0 }

    fun schemeCompleteOn(copyIndex: Int): Boolean =
        schemeLimit?.let { threatOn(copyIndex) >= it } == true

    val schemeComplete: Boolean get() = schemeCompleteOn(0)

    val isFinalVillainStage: Boolean get() = progress.villainIndex >= setup.villain.lastIndex

    val isFinalSchemeStage: Boolean get() = progress.schemeIndex >= setup.scheme.lastIndex

    /**
     * Damage on the villain. A negative amount heals.
     *
     * Stopping at the stage's health rather than running past it: the number
     * beside it is what somebody reads to know the villain is done, and a
     * count of 53/51 tells them nothing they wanted.
     */
    fun damaged(amount: Int): Encounter {
        val raised = (progress.damage + amount).coerceAtLeast(0)
        return withProgress { copy(damage = villainHealth?.let(raised::coerceAtMost) ?: raised) }
    }

    /** Threat on the main scheme. A negative amount thwarts. */
    fun threatened(amount: Int): Encounter = threatened(0, amount)

    /** The same, on one particular copy of the scheme. */
    fun threatened(copyIndex: Int, amount: Int): Encounter {
        val raised = (threatOn(copyIndex) + amount).coerceAtLeast(0)
        val capped = schemeLimit?.let(raised::coerceAtMost) ?: raised
        return withProgress {
            if (copyIndex <= 0) {
                copy(threat = capped)
            } else {
                copy(extraThreats = extraThreats.replacing(copyIndex - 1, capped))
            }
        }
    }

    /**
     * Flips the villain to its next stage, carrying no damage over.
     *
     * Not automatic on reaching the health: defeating a villain stage is a
     * thing the table does, with a step to it and sometimes a choice, and a
     * counter that jumped ahead on its own would be describing a board that
     * does not exist yet.
     */
    fun villainAdvanced(): Encounter =
        if (isFinalVillainStage) {
            this
        } else {
            withProgress {
                copy(
                    villainIndex = villainIndex + 1,
                    damage = 0,
                    manualVillainHealth = null,
                )
            }
        }

    /** Advances the main scheme, starting the new one at its own printed threat. */
    fun schemeAdvanced(): Encounter =
        if (isFinalSchemeStage) {
            this
        } else {
            val next = setup.scheme[progress.schemeIndex + 1]
            val start = next.startingThreatFor(setup.players)
            withProgress {
                copy(
                    schemeIndex = schemeIndex + 1,
                    threat = start,
                    extraThreats = List(schemeCopies - 1) { start },
                    manualSchemeLimit = null,
                )
            }
        }

    /**
     * Ends the round: the acceleration goes on the main scheme.
     *
     * The one piece of arithmetic worth automating — it is per player, it
     * happens every single round, and forgetting it is the commonest way a
     * game ends up somewhere it should not be.
     */
    fun roundEnded(): Encounter {
        val escalation = schemeSide?.escalationFor(setup.players) ?: 0
        // Every copy accelerates, not only the first: a table playing one
        // scheme each is a table where each of them speeds up every round.
        val escalated = if (escalation == 0) {
            this
        } else {
            (0 until schemeCopies).fold(this) { encounter, index ->
                encounter.threatened(index, escalation)
            }
        }
        return escalated.withProgress { copy(round = round + 1) }
    }

    fun withManualVillainHealth(health: Int?): Encounter =
        withProgress { copy(manualVillainHealth = health) }

    fun withManualSchemeLimit(limit: Int?): Encounter =
        withProgress { copy(manualSchemeLimit = limit) }

    private inline fun withProgress(change: EncounterProgress.() -> EncounterProgress) =
        copy(progress = progress.change())

    /** The list with one entry replaced, grown with zeroes if it is short. */
    private fun List<Int>.replacing(index: Int, value: Int): List<Int> {
        val grown = if (size > index) this else this + List(index + 1 - size) { 0 }
        return grown.mapIndexed { at, existing -> if (at == index) value else existing }
    }

    companion object {
        /** A scenario at the start of a game, with the scheme's printed threat on it. */
        fun startOf(setup: EncounterSetup): Encounter {
            val start = setup.scheme.firstOrNull()?.startingThreatFor(setup.players) ?: 0
            return Encounter(
                setup = setup,
                progress = EncounterProgress(
                    threat = start,
                    extraThreats = List((setup.schemeCopies - 1).coerceAtLeast(0)) { start },
                ),
            )
        }
    }
}
