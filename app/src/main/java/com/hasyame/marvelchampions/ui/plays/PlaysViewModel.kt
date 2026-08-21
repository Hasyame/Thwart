package com.hasyame.marvelchampions.ui.plays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The stats screen, already counted.
 *
 * These were computed properties, which meant the headline panel walked the
 * whole history nine times on every recomposition — once per figure. A play
 * history is meant to grow for years, so they are worked out once when the
 * state is built and then just read.
 */
data class PlaysUiState(
    val plays: List<PlayEntity> = emptyList(),
    val byHero: List<WinRateRow> = emptyList(),
    val byScenario: List<WinRateRow> = emptyList(),
    val byAspect: List<WinRateRow> = emptyList(),
    val byDifficulty: List<WinRateRow> = emptyList(),
    val bySoloOrGroup: List<WinRateRow> = emptyList(),
    val byHeroAspect: List<WinRateRow> = emptyList(),
    val totalPlayed: Int = 0,
    val totalWon: Int = 0,
    val totalMillis: Long = 0,
    val averageMillis: Long = 0,
    val longestMillis: Long = 0,
    val campaignPlays: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)

/** Counts a history once, so the screen can read the figures rather than derive them. */
internal fun summarise(
    plays: List<PlayEntity>,
    byHero: List<WinRateRow>,
    byScenario: List<WinRateRow>,
    byAspect: List<WinRateRow>,
    byDifficulty: List<WinRateRow>,
    bySoloOrGroup: List<WinRateRow>,
    byHeroAspect: List<WinRateRow>,
): PlaysUiState {
    var totalMillis = 0L
    var timedCount = 0
    var timedMillis = 0L
    var longest = 0L
    var won = 0
    var campaign = 0
    var bestStreak = 0
    var running = 0

    for (play in plays) {
        totalMillis += play.elapsedMillis
        if (play.elapsedMillis > 0) {
            // Timed games only for the average: a play logged without a
            // duration is a game nobody timed, not a nought-minute game.
            timedCount++
            timedMillis += play.elapsedMillis
        }
        if (play.elapsedMillis > longest) longest = play.elapsedMillis
        if (play.won) won++
        if (play.campaignRunId != null) campaign++

        running = if (play.won) running + 1 else 0
        if (running > bestStreak) bestStreak = running
    }

    return PlaysUiState(
        plays = plays,
        byHero = byHero,
        byScenario = byScenario,
        byAspect = byAspect,
        byDifficulty = byDifficulty,
        bySoloOrGroup = bySoloOrGroup,
        byHeroAspect = byHeroAspect,
        totalPlayed = plays.size,
        totalWon = won,
        totalMillis = totalMillis,
        averageMillis = if (timedCount > 0) timedMillis / timedCount else 0L,
        longestMillis = longest,
        campaignPlays = campaign,
        // The list is newest first, so consecutive wins from the front are the
        // streak the player is currently on.
        currentStreak = plays.takeWhile { it.won }.size,
        bestStreak = bestStreak,
    )
}
/** A play saved but not yet sent, while the app asks whether to send it. */
data class PendingReport(val playId: String, val summary: String)

@HiltViewModel
class PlaysViewModel @Inject constructor(
    private val repository: PlayRepository,
    val photoStore: PhotoStore,
) : ViewModel() {

    /**
     * Two combines, because the typed overload takes five flows and there are
     * seven. A holder rather than a list of Any: casting back out of a list
     * would mean an unchecked suppression for no gain.
     */
    private val counts = combine(
        repository.observePlays(),
        repository.observeByHero(),
        repository.observeByScenario(),
        repository.observeByAspect(),
        repository.observeByDifficulty(),
    ) { plays, byHero, byScenario, byAspect, byDifficulty ->
        Counts(plays, byHero, byScenario, byAspect, byDifficulty)
    }

    private val splits = combine(
        repository.observeBySoloOrGroup(),
        repository.observeByHeroAspect(),
    ) { soloOrGroup, heroAspect -> Splits(soloOrGroup, heroAspect) }

    val uiState: StateFlow<PlaysUiState> = combine(counts, splits) { counted, split ->
        summarise(
            plays = counted.plays,
            byHero = counted.byHero,
            byScenario = counted.byScenario,
            byAspect = counted.byAspect,
            byDifficulty = counted.byDifficulty,
            bySoloOrGroup = split.soloOrGroup,
            byHeroAspect = split.heroAspect,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlaysUiState(),
    )
    private val pending = MutableStateFlow<PendingReport?>(null)
    val pendingReport: StateFlow<PendingReport?> = pending.asStateFlow()

    private val messages = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = messages.asStateFlow()

    fun record(play: PlayEntity) {
        viewModelScope.launch {
            when (val outcome = repository.record(play)) {
                is PlayRecorded.SavedOnly -> Unit
                is PlayRecorded.SavedAndReported -> messages.value = SENT
                is PlayRecorded.SavedAskToReport ->
                    pending.value = PendingReport(play.id, play.scenarioName)

                is PlayRecorded.SavedReportFailed ->
                    messages.value = "Saved. Not sent to BoardGameGeek: ${outcome.detail}"
            }
        }
    }

    /** The answer to "send this one?" — the play is already saved either way. */
    fun confirmReport(playId: String) {
        pending.value = null
        viewModelScope.launch {
            messages.value = when (val outcome = repository.report(playId)) {
                is PlayRecorded.SavedAndReported -> SENT
                is PlayRecorded.SavedReportFailed ->
                    "Not sent to BoardGameGeek: ${outcome.detail}"

                else -> null
            }
        }
    }

    fun dismissReport() {
        pending.value = null
    }

    fun dismissMessage() {
        messages.value = null
    }

    fun delete(playId: String) {
        viewModelScope.launch { repository.delete(playId) }
    }

    /** Sends a play that was skipped or failed earlier. */
    fun reportLater(playId: String) = confirmReport(playId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SENT = "Sent to BoardGameGeek."
    }
}

/** The five counted flows, carried together through the outer combine. */
private data class Counts(
    val plays: List<PlayEntity>,
    val byHero: List<WinRateRow>,
    val byScenario: List<WinRateRow>,
    val byAspect: List<WinRateRow>,
    val byDifficulty: List<WinRateRow>,
)

/** The two splits, likewise. */
private data class Splits(
    val soloOrGroup: List<WinRateRow>,
    val heroAspect: List<WinRateRow>,
)
