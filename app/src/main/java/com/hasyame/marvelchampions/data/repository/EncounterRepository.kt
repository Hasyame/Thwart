package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import com.hasyame.marvelchampions.domain.play.EncounterSide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The numbers a scenario puts on the table, read from the cards.
 *
 * Nothing here is stored or curated: the villain's health and the scheme's
 * threat limit come from the same card rows the Cards tab shows, so a scenario
 * added to the card database works without this app being touched.
 */
@Singleton
class EncounterRepository @Inject constructor(
    private val cardDao: CardDao,
    private val preferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun setupFor(scenarioCode: String, players: Int): EncounterSetup =
        withContext(ioDispatcher) {
            val locale = preferences.cardLocale.first()
            versusHalves(scenarioCode)?.let { (side, leader) ->
                return@withContext versusSetup(side, leader, players, locale.code)
            }
            val rows = cardDao.getScenarioSides(scenarioCode, locale.code)

            EncounterSetup(
                villain = rows.filter { it.typeCode in VILLAIN_TYPES && !it.doubleSided }
                    .map(::villainSide),
                scheme = rows.filter { it.typeCode == MAIN_SCHEME && it.isNumbersSide }
                    .map(::schemeSide),
                players = players.coerceAtLeast(1),
            )
        }

    /**
     * A versus scenario's side and leader, or null for an ordinary scenario.
     *
     * The randomiser builds the pair's code, and this reads it back. Kept
     * beside the reader rather than shared as a helper because the shape of
     * that code is this file's problem and nobody else's.
     */
    private fun versusHalves(scenarioCode: String): Pair<String, String>? {
        val parts = scenarioCode.split(VERSUS_SEPARATOR)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    /**
     * The leader's stages, and no scheme.
     *
     * Each side holds four stage-1 schemes and four stage-2 schemes because
     * the players mix and match, so which one is on the table is not knowable
     * from the pair alone. A guessed threat limit is worse than none: the
     * counter shows a count with no target, which the panel already handles.
     */
    private suspend fun versusSetup(
        side: String,
        leader: String,
        players: Int,
        locale: String,
    ): EncounterSetup {
        val rows = cardDao.getScenarioSides(leader, locale)
        return EncounterSetup(
            villain = rows.filter { it.typeCode in VILLAIN_TYPES && !it.doubleSided }
                .map(::villainSide),
            scheme = emptyList(),
            players = players.coerceAtLeast(1),
        )
    }

    /**
     * The side of a main scheme that carries the numbers.
     *
     * The card database holds each scheme three times: side A with the setup
     * text, side B with the threat, and a combined double-sided row that
     * repeats both. Taking everything counted each stage twice and put the
     * text side — which has no threat limit — in front of the numbers side.
     * Every scenario in the database has a B side, so this loses nothing.
     */
    private val CardEntity.isNumbersSide: Boolean
        get() = !doubleSided && stage.orEmpty().endsWith("B", ignoreCase = true)

    private fun villainSide(card: CardEntity) = EncounterSide(
        name = card.name,
        stage = card.stage.orEmpty(),
        value = card.health,
        // The card database says "per hero" here, where true means multiply.
        perPlayer = card.healthPerHero,
        starred = card.healthStar,
    )

    private fun schemeSide(card: CardEntity) = EncounterSide(
        name = card.name,
        stage = card.stage.orEmpty(),
        // A printed limit of zero means this stage has no threat limit at all
        // — it advances some other way, as The Brotherhood Strikes! does when
        // the villains are defeated. Read literally it made the scheme
        // "complete" the moment the game started.
        value = card.threat?.takeIf { it > 0 },
        // ...and "fixed" here, where **false** means multiply. Same idea,
        // opposite spelling, which is exactly why it is normalised once.
        perPlayer = !card.threatFixed,
        starred = card.threatStar,
        startingThreat = card.baseThreat ?: 0,
        startingThreatPerPlayer = !card.baseThreatFixed,
        escalation = card.escalationThreat ?: 0,
        escalationPerPlayer = !card.escalationThreatFixed,
    )

    private companion object {
        /** How the randomiser joins a versus side to its leader. */
        const val VERSUS_SEPARATOR = "__"

        const val VILLAIN = "villain"

        /** Civil War's leaders sit in the villain's place and behave as one. */
        val VILLAIN_TYPES = setOf(VILLAIN, "leader")
        const val MAIN_SCHEME = "main_scheme"
    }
}
