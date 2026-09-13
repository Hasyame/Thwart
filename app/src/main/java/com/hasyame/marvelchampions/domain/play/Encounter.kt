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
    /**
     * The card prints a star where the acceleration goes, so the amount is
     * whatever the board says at the time.
     *
     * Cambriolage du Musée d'Art accelerates by the number of ART attachments
     * on the villain plus one, which grows as the game goes on. The app cannot
     * see the table, so it adds nothing and the round button only advances the
     * round: a tracker that half-adjudicates is wrong at somebody's table, and
     * then the numbers it *is* keeping stop being trusted either.
     */
    val escalationVariable: Boolean = false,
) {
    fun totalFor(players: Int): Int? = value?.timesPlayers(perPlayer, players)

    fun startingThreatFor(players: Int): Int =
        startingThreat.timesPlayers(startingThreatPerPlayer, players) + extraStartingThreat

    fun escalationFor(players: Int): Int =
        if (escalationVariable) 0 else escalation.timesPlayers(escalationPerPlayer, players)

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
    /**
     * Villains fought at the same time, beyond the first: each its own stages,
     * each its own damage. Tower Defense puts Proxima Midnight and Corvus
     * Glaive on the table together, and a tracker that walked them one after
     * the other was counting a fight nobody was having.
     */
    val moreVillains: List<List<EncounterSide>> = emptyList(),
    /**
     * True when the villains fall together: none is defeated while another
     * has hit points left, and they turn to their next stage as one. Printed
     * on both Tower Defense villains. Damage past a villain's health still
     * stops at the health; what it cannot do is count as a defeat on its own.
     */
    val villainsLinked: Boolean = false,
    /**
     * Main schemes advancing side by side, beyond the first: each its own
     * stages, its own threat, its own acceleration at the end of the round.
     */
    val moreSchemes: List<List<EncounterSide>> = emptyList(),
    /**
     * A scheme that clears rather than completes: when its threat reaches the
     * limit the card removes all of it and does something else instead, so
     * the button at the limit resets the count rather than turning a stage.
     * Both Tower Defense schemes are printed this way.
     */
    val schemesReset: Boolean = false,
    /** A card with hit points of its own, in play beside the villain. */
    val structure: StructureSetup? = null,
) {
    /** Nothing to count is not worth showing. */
    val isUsable: Boolean get() = villain.isNotEmpty() || scheme.isNotEmpty()

    /** Every villain on the table, the first included. */
    val villainTracks: List<List<EncounterSide>> get() = listOf(villain) + moreVillains

    /** Every main scheme on the table, the first included. */
    val schemeTracks: List<List<EncounterSide>> get() = listOf(scheme) + moreSchemes
}

/**
 * A card with hit points that is not a villain: Avengers Tower, which takes
 * damage on its Stronghold side, turns over when it has taken enough, and
 * loses the game for the players when its second side has too.
 *
 * Only counters, as everywhere here. Reaching a side's limit offers the turn
 * rather than making it, and the last side's limit is reported, not acted on:
 * losing is the table's to declare.
 */
data class StructureSetup(
    val name: String,
    /** The sides in the order they are turned through; each with its limit. */
    val sides: List<EncounterSide>,
    /** Damage already on the card when the game starts, worked out. */
    val startingDamage: Int = 0,
)

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
    /**
     * The second and later villains, where the first stays in the fields
     * above: a game put away before these existed wrote those into its saved
     * counters, and moving them would bring every paused game back empty.
     */
    val moreVillains: List<TrackProgress> = emptyList(),
    /** The second and later main schemes, likewise. */
    val moreSchemes: List<TrackProgress> = emptyList(),
    /** Which side of the structure is up, and the damage on it. */
    val structureIndex: Int = 0,
    val structureDamage: Int = 0,
)

/** Where one further track stands: its stage, its count, and a typed limit for a star. */
@Serializable
data class TrackProgress(
    val index: Int = 0,
    val value: Int = 0,
    val manual: Int? = null,
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

    val villainDefeated: Boolean get() = villainDefeatedAt(0)

    // --- several villains at once --------------------------------------------

    val villainTrackCount: Int get() = setup.villainTracks.size

    private fun villainProgressAt(track: Int): TrackProgress =
        if (track <= 0) {
            TrackProgress(progress.villainIndex, progress.damage, progress.manualVillainHealth)
        } else {
            progress.moreVillains.getOrElse(track - 1) { TrackProgress() }
        }

    fun villainSideAt(track: Int): EncounterSide? =
        setup.villainTracks.getOrNull(track)?.getOrNull(villainProgressAt(track).index)

    fun villainHealthAt(track: Int): Int? =
        villainSideAt(track)?.totalFor(setup.players) ?: villainProgressAt(track).manual

    fun villainDamageAt(track: Int): Int = villainProgressAt(track).value

    /** This villain is at its health; a defeat only if none of the others still stands. */
    fun villainDownAt(track: Int): Boolean = villainHealthAt(track)?.let { villainDamageAt(track) >= it } == true

    fun villainDefeatedAt(track: Int): Boolean =
        if (setup.villainsLinked) {
            (0 until villainTrackCount).all { villainDownAt(it) }
        } else {
            villainDownAt(track)
        }

    fun isFinalVillainStageAt(track: Int): Boolean =
        villainProgressAt(track).index >= (setup.villainTracks.getOrNull(track)?.lastIndex ?: 0)

    /** Damage on one villain. A negative amount heals. Stops at the stage's health, as [damaged] does. */
    fun damagedAt(track: Int, amount: Int): Encounter {
        if (track <= 0) {
            return damaged(amount)
        }
        val raised = (villainDamageAt(track) + amount).coerceAtLeast(0)
        val capped = villainHealthAt(track)?.let(raised::coerceAtMost) ?: raised
        return withVillainProgress(track) { copy(value = capped) }
    }

    /**
     * Turns one villain to its next stage; linked villains turn together.
     *
     * Together, because a table that has brought both to zero turns both
     * cards, and a tracker that turned one and left the other at full damage
     * on a stage nobody was playing would be describing no board at all.
     */
    fun villainAdvancedAt(track: Int): Encounter =
        if (setup.villainsLinked) {
            (0 until villainTrackCount).fold(this) { encounter, each -> encounter.advanceOneVillain(each) }
        } else {
            advanceOneVillain(track)
        }

    private fun advanceOneVillain(track: Int): Encounter {
        if (track <= 0) {
            return villainAdvanced()
        }
        if (isFinalVillainStageAt(track)) {
            return this
        }
        return withVillainProgress(track) { copy(index = index + 1, value = 0, manual = null) }
    }

    private inline fun withVillainProgress(track: Int, change: TrackProgress.() -> TrackProgress): Encounter {
        val current = villainProgressAt(track)
        return withProgress {
            copy(moreVillains = moreVillains.replacing(track - 1, current.change(), TrackProgress()))
        }
    }

    // --- several main schemes at once ------------------------------------------

    val schemeTrackCount: Int get() = setup.schemeTracks.size

    private fun schemeProgressAt(track: Int): TrackProgress =
        if (track <= 0) {
            TrackProgress(progress.schemeIndex, progress.threat, progress.manualSchemeLimit)
        } else {
            progress.moreSchemes.getOrElse(track - 1) { TrackProgress() }
        }

    fun schemeSideAt(track: Int): EncounterSide? =
        setup.schemeTracks.getOrNull(track)?.getOrNull(schemeProgressAt(track).index)

    fun schemeLimitAt(track: Int): Int? =
        schemeSideAt(track)?.totalFor(setup.players) ?: schemeProgressAt(track).manual

    /** Threat on one copy of one main scheme. Copies exist on the first track only. */
    fun threatOnTrack(track: Int, copyIndex: Int): Int =
        if (track <= 0) threatOn(copyIndex) else schemeProgressAt(track).value

    fun schemeCompleteAt(track: Int, copyIndex: Int = 0): Boolean =
        schemeLimitAt(track)?.let { threatOnTrack(track, copyIndex) >= it } == true

    fun isFinalSchemeStageAt(track: Int): Boolean =
        schemeProgressAt(track).index >= (setup.schemeTracks.getOrNull(track)?.lastIndex ?: 0)

    fun threatenedAt(track: Int, copyIndex: Int, amount: Int): Encounter {
        if (track <= 0) {
            return threatened(copyIndex, amount)
        }
        val raised = (threatOnTrack(track, 0) + amount).coerceAtLeast(0)
        val capped = schemeLimitAt(track)?.let(raised::coerceAtMost) ?: raised
        return withSchemeProgress(track) { copy(value = capped) }
    }

    /**
     * Advances one main scheme; or, for a scheme that clears rather than
     * completes, removes all its threat and leaves the stage where it is.
     */
    fun schemeAdvancedAt(track: Int): Encounter {
        if (setup.schemesReset) {
            return if (track <= 0) {
                withProgress { copy(threat = 0, extraThreats = extraThreats.map { 0 }) }
            } else {
                withSchemeProgress(track) { copy(value = 0) }
            }
        }
        if (track <= 0) {
            return schemeAdvanced()
        }
        if (isFinalSchemeStageAt(track)) {
            return this
        }
        val next = setup.schemeTracks[track][schemeProgressAt(track).index + 1]
        return withSchemeProgress(track) {
            copy(index = index + 1, value = next.startingThreatFor(setup.players), manual = null)
        }
    }

    private inline fun withSchemeProgress(track: Int, change: TrackProgress.() -> TrackProgress): Encounter {
        val current = schemeProgressAt(track)
        return withProgress {
            copy(moreSchemes = moreSchemes.replacing(track - 1, current.change(), TrackProgress()))
        }
    }

    // --- a card with hit points of its own ---------------------------------------

    val structureSide: EncounterSide? get() = setup.structure?.sides?.getOrNull(progress.structureIndex)

    val structureLimit: Int? get() = structureSide?.totalFor(setup.players)

    /** The side has taken all it can. */
    val structureFull: Boolean get() = structureLimit?.let { progress.structureDamage >= it } == true

    val isFinalStructureSide: Boolean
        get() = progress.structureIndex >= (setup.structure?.sides?.lastIndex ?: 0)

    /** The last side is full: the card says the players lose. Reported, never acted on. */
    val structureLost: Boolean get() = structureFull && isFinalStructureSide

    fun structureDamaged(amount: Int): Encounter {
        val raised = (progress.structureDamage + amount).coerceAtLeast(0)
        val capped = structureLimit?.let(raised::coerceAtMost) ?: raised
        return withProgress { copy(structureDamage = capped) }
    }

    /** Turns the card over: the damage comes off with it, as the card says. */
    fun structureTurned(): Encounter =
        if (isFinalStructureSide) {
            this
        } else {
            withProgress { copy(structureIndex = structureIndex + 1, structureDamage = 0) }
        }

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
        // And every scheme beside the first, each by its own printed amount.
        val allEscalated = (1 until schemeTrackCount).fold(escalated) { encounter, track ->
            val own = encounter.schemeSideAt(track)?.escalationFor(setup.players) ?: 0
            if (own == 0) encounter else encounter.threatenedAt(track, 0, own)
        }
        return allEscalated.withProgress { copy(round = round + 1) }
    }

    fun withManualVillainHealth(health: Int?): Encounter =
        withProgress { copy(manualVillainHealth = health) }

    fun withManualSchemeLimit(limit: Int?): Encounter =
        withProgress { copy(manualSchemeLimit = limit) }

    private inline fun withProgress(change: EncounterProgress.() -> EncounterProgress) =
        copy(progress = progress.change())

    /** The list with one entry replaced, grown with zeroes if it is short. */
    private fun List<Int>.replacing(index: Int, value: Int): List<Int> = replacing(index, value, 0)

    private fun <T> List<T>.replacing(index: Int, value: T, filler: T): List<T> {
        val grown = if (size > index) this else this + List(index + 1 - size) { filler }
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
                    moreSchemes = setup.moreSchemes.map { stages ->
                        TrackProgress(value = stages.firstOrNull()?.startingThreatFor(setup.players) ?: 0)
                    },
                    structureDamage = setup.structure?.startingDamage ?: 0,
                ),
            )
        }
    }
}
