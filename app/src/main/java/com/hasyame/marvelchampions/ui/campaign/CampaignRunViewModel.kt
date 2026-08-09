package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where a run currently is, as one page at a time rather than a single long
 * screen.
 */
enum class RunPage {
    /** Title, story, base setup and campaign setup. */
    BRIEFING,

    /** Timer running, with the two outcome buttons. */
    PLAYING,

    /** The post-victory questionnaire. */
    QUESTIONS,

    /** What the scenario awarded, and where to go next. */
    RESULT,

    /** What was just lost, with replay or leave. */
    DEFEAT,

    /** The market, reachable from [RESULT] or on its own. */
    MARKET,

    /** Choosing which scenario to play next. */
    CHOICE,
}

/** What one scenario awarded, for the result page. */
data class ScenarioOutcomeSummary(
    val victory: Boolean,
    val elapsedMillis: Long,
    val victoryPoints: Int?,
    /** Hero id to credits gained in this scenario alone. */
    val creditsGained: Map<String, Int> = emptyMap(),
    val nextScenarioName: String? = null,
    val campaignFinished: Boolean = false,
)

data class CampaignRunUiState(
    val run: CampaignRun? = null,
    val page: RunPage = RunPage.BRIEFING,
    val elapsedMillis: Long = 0,
    val isLoading: Boolean = true,
    val summary: ScenarioOutcomeSummary? = null,
    /** Ambient playlist offered on the play page. */
    val musicUrl: String = "",
    /**
     * True while a scenario result is being filed.
     *
     * Recording appends to the campaign log, records a play and may report to
     * BoardGameGeek, none of which is instant. Without this the screen sat
     * unchanged and a second tap filed the whole lot again.
     */
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class CampaignRunViewModel @Inject constructor(
    private val repository: CampaignRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(CampaignRunUiState())
    val uiState: StateFlow<CampaignRunUiState> = state.asStateFlow()

    private var runId: String? = null

    fun load(id: String) {
        if (runId == id) {
            return
        }
        runId = id
        viewModelScope.launch {
            preferences.musicUrl.collect { url ->
                state.value = state.value.copy(musicUrl = url)
            }
        }
        viewModelScope.launch {
            reload()
            // A run reopened with the timer already going belongs on the play
            // page, not back at the briefing.
            val running = state.value.run?.timer?.isRunning == true
            val awaiting = state.value.run?.state?.awaitingChoice == true
            state.value = state.value.copy(
                page = when {
                    awaiting -> RunPage.CHOICE
                    running -> RunPage.PLAYING
                    else -> RunPage.BRIEFING
                },
            )
        }
    }

    private suspend fun reload() {
        val id = runId ?: return
        val locale = preferences.currentCardLocale()
        // Before the run is read, so the briefing shows its drawn cards on the
        // first frame rather than filling them in a moment later.
        repository.ensureSetupDraws(id, locale)
        val run = repository.load(id, locale)
        state.value = state.value.copy(
            run = run,
            elapsedMillis = run?.timer?.elapsedAt(System.currentTimeMillis()) ?: 0,
            isLoading = false,
        )
    }

    fun tick() {
        val timer = state.value.run?.timer ?: return
        state.value = state.value.copy(elapsedMillis = timer.elapsedAt(System.currentTimeMillis()))
    }

    fun goTo(page: RunPage) {
        state.value = state.value.copy(page = page)
    }

    /** "I'm ready" — starts the clock and moves to the play page. */
    fun beginScenario() {
        val id = runId ?: return
        val run = state.value.run ?: return
        viewModelScope.launch {
            repository.updateTimer(
                id,
                TimerState().start(System.currentTimeMillis()),
                run.state.currentScenarioId,
            )
            reload()
            state.value = state.value.copy(page = RunPage.PLAYING)
        }
    }

    fun pauseTimer() = updateTimer { it.pause(System.currentTimeMillis()) }

    fun resumeTimer() = updateTimer { it.start(System.currentTimeMillis()) }

    /** The stop button gets forgotten, so the elapsed time is editable. */
    fun setElapsed(millis: Long) = updateTimer { it.setElapsed(millis, System.currentTimeMillis()) }

    private fun updateTimer(transform: (TimerState) -> TimerState) {
        val id = runId ?: return
        val run = state.value.run ?: return
        viewModelScope.launch {
            repository.updateTimer(id, transform(run.timer), run.state.currentScenarioId)
            reload()
        }
    }

    /** Victory: pause the clock and ask the questions. */
    fun declareVictory() {
        val id = runId ?: return
        val run = state.value.run ?: return
        viewModelScope.launch {
            repository.updateTimer(
                id,
                run.timer.pause(System.currentTimeMillis()),
                run.state.currentScenarioId,
            )
            reload()
            state.value = state.value.copy(page = RunPage.QUESTIONS)
        }
    }

    fun declareDefeat() {
        val id = runId ?: return
        val run = state.value.run ?: return
        val scenarioId = run.state.currentScenarioId ?: return
        if (state.value.isSubmitting) {
            return
        }
        state.value = state.value.copy(isSubmitting = true)
        viewModelScope.launch {
            val elapsed = run.timer.elapsedAt(System.currentTimeMillis())
            repository.append(
                id,
                CampaignEvent.ScenarioCompleted(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    scenarioId = scenarioId,
                    victory = false,
                    elapsedMillis = elapsed,
                ),
            )
            repository.updateTimer(id, TimerState(), scenarioId)
            // A lost scenario is still a game that was played, so it counts
            // towards win rates like any other.
            recordPlay(id, scenarioId, won = false, elapsedMillis = elapsed)
            reload()
            state.value = state.value.copy(
                page = RunPage.DEFEAT,
                isSubmitting = false,
                summary = ScenarioOutcomeSummary(victory = false, elapsedMillis = elapsed, victoryPoints = null),
            )
        }
    }

    /** Records the questionnaire and moves to the result page. */
    fun submitAnswers(answers: AnswerSet) {
        val id = runId ?: return
        val run = state.value.run ?: return
        val scenarioId = run.state.currentScenarioId
        if (scenarioId == null) {
            // Returning quietly here made the Validate button look broken.
            state.value = state.value.copy(page = RunPage.RESULT, summary = null)
            return
        }
        if (state.value.isSubmitting) {
            return
        }
        state.value = state.value.copy(isSubmitting = true)

        viewModelScope.launch {
            val elapsed = run.timer.elapsedAt(System.currentTimeMillis())
            repository.append(
                id,
                CampaignEvent.ScenarioCompleted(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    scenarioId = scenarioId,
                    victory = true,
                    answers = answers,
                    elapsedMillis = elapsed,
                ),
            )
            repository.updateTimer(id, TimerState(), scenarioId)
            recordPlay(
                id,
                scenarioId,
                won = true,
                elapsedMillis = elapsed,
                // The questionnaire already asked; BoardGameGeek records it as
                // the player score.
                victoryPoints = answers.numbers["vp"] ?: 0,
            )
            reload()

            val reloaded = state.value.run
            val before = reloaded?.stateBeforeLastScenario
            val counterId = reloaded?.template?.market?.counterId ?: "credits"
            // Gained in this scenario alone, derived by folding the log with and
            // without the result rather than by storing a running total.
            val gained = reloaded?.state?.heroes.orEmpty().associate { hero ->
                hero.id to (
                    (reloaded?.state?.heroCounter(counterId, hero.id) ?: 0) -
                        (before?.heroCounter(counterId, hero.id) ?: 0)
                    )
            }
            val nextId = reloaded?.state?.currentScenarioId
            val finished = reloaded?.state?.finished == true

            if (finished) {
                repository.markFinished(id, true)
            }

            state.value = state.value.copy(
                page = RunPage.RESULT,
                isSubmitting = false,
                summary = ScenarioOutcomeSummary(
                    victory = true,
                    elapsedMillis = elapsed,
                    victoryPoints = answers.numbers["vp"],
                    creditsGained = gained,
                    nextScenarioName = nextId
                        ?.takeIf { it != scenarioId }
                        ?.let { next ->
                            reloaded?.template?.scenarios?.firstOrNull { it.id == next }
                                ?.name?.resolve(reloaded.localeCode)
                        },
                    campaignFinished = finished,
                ),
            )
        }
    }

    /** Deletes the run and everything recorded for it. */
    fun forgetCampaign(onDone: () -> Unit) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.deleteRun(id)
            onDone()
        }
    }

    /** "I can do this all day" — same scenario, clock from zero. */
    fun replayScenario() {
        state.value = state.value.copy(summary = null, page = RunPage.BRIEFING)
    }

    fun continueToNextScenario() {
        state.value = state.value.copy(summary = null, page = RunPage.BRIEFING)
    }

    fun purchase(heroId: String, cardCode: String, cost: Int, cardListId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.MarketPurchase(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    heroId = heroId,
                    cardCode = cardCode,
                    cost = cost,
                    cardListId = cardListId,
                ),
            )
            reload()
        }
    }

    fun refund(purchaseEventId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.MarketRefund(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    purchaseEventId = purchaseEventId,
                ),
            )
            reload()
        }
    }

    /** Records which scenario the players chose, and opens its briefing. */
    fun chooseScenario(scenarioId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.chooseScenario(id, scenarioId)
            reload()
            state.value = state.value.copy(page = RunPage.BRIEFING)
        }
    }

    /** Keeps one of the cards a draw offered; the rest go back to the pool. */
    fun keepDrawnCard(drawId: String, cardCode: String) {
        val id = runId ?: return
        val scenarioId = state.value.run?.state?.currentScenarioId ?: return
        viewModelScope.launch {
            repository.chooseDrawnCard(id, scenarioId, drawId, cardCode)
            reload()
        }
    }

    fun takeSetupAction(actionId: String, heroId: String?) {
        val id = runId ?: return
        val scenarioId = state.value.run?.state?.currentScenarioId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.SetupActionTaken(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    scenarioId = scenarioId,
                    actionId = actionId,
                    heroId = heroId,
                ),
            )
            reload()
        }
    }

    fun adjust(counterId: String?, heroId: String?, value: Int?, note: String?) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.ManualAdjustment(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    counterId = counterId,
                    heroId = heroId,
                    value = value,
                    note = note,
                ),
            )
            reload()
        }
    }

    fun revoke(eventId: String, note: String?) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.EventRevoked(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    revokedEventId = eventId,
                    note = note,
                ),
            )
            reload()
        }
    }

    /**
     * Adds a finished scenario to the play log.
     *
     * Deliberately not allowed to disturb the campaign: the scenario result is
     * already recorded in the event log, and a failure to also file it as a
     * play must never leave the run in a strange state.
     */
    private suspend fun recordPlay(
        id: String,
        scenarioId: String,
        won: Boolean,
        elapsedMillis: Long,
        victoryPoints: Int = 0,
    ) {
        runCatching {
            repository.recordScenarioPlay(
                runId = id,
                scenarioId = scenarioId,
                won = won,
                elapsedMillis = elapsedMillis,
                victoryPoints = victoryPoints,
                locale = preferences.currentCardLocale(),
            )
        }
    }
}
