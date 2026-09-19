package com.hasyame.marvelchampions.domain.achievements

import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import com.hasyame.marvelchampions.domain.play.FearNoEvil

/**
 * This app's records, turned into the facts the derivation reads:
 * docs/spec/achievements/algorithm.md §2, applied to the phone's own
 * storage shape. Deterministic given the record, the run and the template;
 * the campaign resolution is handed in as a function so this stays free of
 * the card database and the templates, and so a test can stand one in.
 */
object AchievementFacts {

    /** A campaign play's scenario key through its run's template, or null when nothing resolves. */
    fun interface CampaignResolver {
        fun scenarioKeyOf(play: PlayEntity): String?
    }

    /** The scenario key of a play: data-model.md §3. */
    fun scenarioKeyOf(play: PlayEntity, resolve: CampaignResolver): String {
        if (play.campaignRunId != null) {
            return resolve.scenarioKeyOf(play) ?: "campaign:${play.scenarioCode}"
        }
        if (FearNoEvil.isFne(play.scenarioCode)) {
            // The villain half is dropped: the job is the scenario.
            return FearNoEvil.split(play.scenarioCode).first
        }
        return play.scenarioCode
    }

    /** An aspect string as a set of codes: split on commas, trimmed, lowercased, sorted. */
    fun aspectSet(aspect: String): List<String> =
        aspect.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()

    /** The seats, exactly one of them the owner's: the flagged one, else the first. */
    fun seatsOf(play: PlayEntity): List<Seat> {
        val roster: List<PlayHero> = play.roster.ifEmpty {
            listOf(PlayHero(code = play.heroCode, name = play.heroName, aspect = play.aspects, isOwner = true))
        }
        val flagged = roster.indexOfFirst { it.isOwner == true }
        val owner = if (flagged < 0) 0 else flagged
        return roster.mapIndexed { i, seat -> Seat(seat.code, aspectSet(seat.aspect), isOwner = i == owner) }
    }

    /** A play as a fact, or null for a tombstone. */
    fun factOf(play: PlayEntity, resolve: CampaignResolver): PlayFact? {
        if (play.deletedAt != null) {
            return null
        }
        val seats = seatsOf(play)
        return PlayFact(
            id = play.id,
            playedAt = play.playedAt,
            scenarioKey = scenarioKeyOf(play, resolve),
            level = DifficultyLevel.of(play.difficulty),
            won = play.won,
            players = play.players.coerceIn(1, 4),
            seats = seats,
            campaignRunId = play.campaignRunId,
            mode = play.mode?.takeIf { it in PLAY_MODES },
        )
    }

    /** A run as a fact; [lost] is the engine's fold of its log, false when the template cannot be read. */
    fun runFactOf(run: CampaignRunEntity, lost: Boolean): RunFact = RunFact(
        id = run.id,
        templateId = run.templateId,
        level = DifficultyLevel.of(run.difficulty),
        finished = run.finished,
        lost = lost,
    )
}
