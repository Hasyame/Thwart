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

    /**
     * Keeping one of the environments the villains hit this rotation.
     *
     * Only campaigns that deal environments reach this; the rest go straight to
     * the scenario choice.
     */
    ENVIRONMENT,

    /** A place fell and took the campaign with it. */
    CAMPAIGN_LOST,
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
            reload()
            // A run reopened with the timer already going belongs on the play
            // page, not back at the briefing.
            state.value = state.value.copy(page = pageFor(state.value.run))
        }
    }

    private suspend fun reload() {
        val id = runId ?: return
        val locale = preferences.currentCardLocale()
        // Before the run is read, so the briefing shows its drawn cards on the
        // first frame rather than filling them in a moment later.
        repository.ensureSetupDraws(id, locale)
        // Who is behind which job, settled once at the start of the campaign
        // and kept quiet until the players get there.
        repository.ensureVillainAssignment(id, locale)
        // The rotation's environments, dealt before the players choose so the
        // pressure is already on the board when they weigh where to go.
        repository.ensureEnvironmentOffer(id, locale)
        val run = repository.load(id, locale)
        // A campaign lost to a fallen place ends outside the victory path, so
        // it is filed here instead. Without this the run stayed open in the
        // list and the defeat was never recorded.
        if (run?.state?.campaignLost == true) {
            repository.markFinished(id, true)
        }
        state.value = state.value.copy(
            run = run,
            elapsedMillis = run?.timer?.elapsedAt(System.currentTimeMillis()) ?: 0,
            isLoading = false,
        )
    }

    /**
     * Which page a run belongs on, from its state alone.
     *
     * One place rather than a decision repeated at each transition: a campaign
     * that can end between two taps has too many ways to be got wrong.
     */
    private fun pageFor(run: CampaignRun?): RunPage {
        val campaign = run?.state ?: return RunPage.BRIEFING
        return when {
            campaign.campaignLost -> RunPage.CAMPAIGN_LOST
            campaign.awaitingChoice && campaign.environmentOffer.isNotEmpty() -> RunPage.ENVIRONMENT
            campaign.awaitingChoice -> RunPage.CHOICE
            run.timer.isRunning -> RunPage.PLAYING
            else -> RunPage.BRIEFING
        }
    }

    /**
     * The players stop at the last villain and take the loss.
     *
     * The rules let a table retry the finale for as long as they can stand it,
     * so ending there is theirs to decide — and once decided it is a defeat,
     * recorded like any other.
     */
    fun concedeCampaign() {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.CampaignConceded(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                ),
            )
            reload()
            state.value = state.value.copy(page = pageFor(state.value.run))
        }
    }

    /** Keeps one of the dealt environments and moves on to the scenario choice. */
    fun chooseEnvironment(environmentId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.chooseEnvironment(id, environmentId)
            reload()
            state.value = state.value.copy(page = pageFor(state.value.run))
        }
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
                            reloaded.template.scenarios.firstOrNull { it.id == next }
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
        // Not straight to a briefing: a campaign that deals environments starts
        // its next rotation here, and one may already have fallen.
        state.value = state.value.copy(summary = null, page = pageFor(state.value.run))
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
