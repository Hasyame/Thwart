package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.PausedGameDao
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import com.hasyame.marvelchampions.data.db.entity.PausedPhase
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.ui.plays.LongBreakDraft
import java.util.UUID
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.repository.EncounterRepository
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.domain.play.EncounterProgress
import com.hasyame.marvelchampions.domain.campaign.template.TrackedSide
import com.hasyame.marvelchampions.domain.play.EncounterSide
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import kotlinx.coroutines.flow.first
import com.hasyame.marvelchampions.domain.campaign.template.villainStages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
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
    /** The scenario's own closing line, placeholders already resolved. */
    val outcomeMessage: String? = null,
    /** The scenario this result belongs to, which the run may have left. */
    val scenarioId: String? = null,
    /**
     * Whether this campaign lets a lost scenario be left behind.
     *
     * Fear No Evil does: a failed job leaves the campaign and the rest goes
     * on. No other campaign has that rule, so offering it everywhere invented
     * a choice the players do not have.
     */
    val canContinue: Boolean = false,
)

data class CampaignRunUiState(
    /**
     * What is being written down while the scenario is put away.
     *
     * Null except between pressing the button and saving, exactly as in a
     * standalone game: a campaign scenario is a game like any other and
     * gets cleared off a table for the same reasons.
     */
    val longBreak: LongBreakDraft? = null,
    val run: CampaignRun? = null,
    val page: RunPage = RunPage.BRIEFING,
    val elapsedMillis: Long = 0,
    val isLoading: Boolean = true,
    val summary: ScenarioOutcomeSummary? = null,
    /**
     * True while a scenario result is being filed.
     *
     * Recording appends to the campaign log, records a play and may report to
     * BoardGameGeek, none of which is instant. Without this the screen sat
     * unchanged and a second tap filed the whole lot again.
     */
    val isSubmitting: Boolean = false,

    /** Counting villain health and scheme threat, if the setting asks for it. */
    val trackEncounter: Boolean = false,

    /**
     * Whether the player has the tracker switched on at all.
     *
     * Kept apart from [trackEncounter], which also needs numbers to count. The
     * two together are what let the screen tell "you turned this off", which
     * needs no comment, from "you turned this on and this scenario has nothing
     * to count", which was previously an empty space with no explanation.
     */
    val trackerWanted: Boolean = false,
    val encounter: Encounter = Encounter.startOf(EncounterSetup()),
    val keepAwake: Boolean = true,
)

/**
 * A curated side, as the tracker's own model.
 *
 * The numbers are floored at zero. A bundled campaign is checked by a test, but
 * a template can also be imported from a file the player was given, and a
 * negative health would make the villain defeated the moment the game started,
 * which the tracker would report as fact.
 */
private fun TrackedSide.toSide(): EncounterSide = EncounterSide(
    name = name,
    stage = stage,
    value = value?.coerceAtLeast(0),
    perPlayer = perPlayer,
    starred = starred,
    startingThreat = startingThreat.coerceAtLeast(0),
    startingThreatPerPlayer = startingThreatPerPlayer,
    escalation = escalation.coerceAtLeast(0),
    escalationPerPlayer = escalationPerPlayer,
)

@HiltViewModel
class CampaignRunViewModel @Inject constructor(
    private val repository: CampaignRepository,
    private val preferences: AppPreferences,
    private val encounterRepository: EncounterRepository,
    private val cardDao: CardDao,
    private val pausedGameDao: PausedGameDao,
    private val json: Json,
    val photoStore: PhotoStore,
) : ViewModel() {

    private val state = MutableStateFlow(CampaignRunUiState())
    val uiState: StateFlow<CampaignRunUiState> = state.asStateFlow()

    private var runId: String? = null

    /**
     * The language the app is being shown in, told to us by the screen.
     *
     * A campaign's own words follow the app's language, not the card language,
     * and a view model has no composition to read the configuration from.
     */
    private var textLocale: String? = null

    fun onAppLanguage(code: String) {
        textLocale = code
    }

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

    /**
     * Files the rotation's draw so it is not dealt again.
     *
     * Nothing is chosen here — the rules draw two and tick the jobs they name.
     * This only records that the table has read it, which is what stops the
     * next reload dealing another pair.
     */
    fun acknowledgeEnvironments() {
        val id = runId ?: return
        viewModelScope.launch {
            repository.acknowledgeEnvironments(id)
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

    /**
     * Builds the tracker for the scenario about to be played.
     *
     * The set is read off the villain rather than declared in the template:
     * every campaign already names its villain by card code, and the card
     * knows which set it belongs to, so this works for all seven without
     * touching any of them. A scenario whose cards are in no database — Fear
     * No Evil — simply produces nothing usable and the panel stays away.
     */
    private suspend fun buildEncounter(run: CampaignRun): EncounterSetup {
        if (!preferences.trackEncounter.first()) {
            return EncounterSetup()
        }
        val scenarioId = run.state.currentScenarioId ?: return EncounterSetup()
        val scenario = run.template.scenarios
            .firstOrNull { it.id == scenarioId }
            ?: return EncounterSetup()

        // A campaign that carries its own numbers is believed before the card
        // database is asked. Only Fear No Evil does, and only because MarvelCDB
        // has none of its villains; every other campaign leaves this empty and
        // is read from the database as it always was.
        run.template.tracker?.let { tracker ->
            val drawn = CampaignEngine.drawnCards(
                run.state,
                scenarioId,
                CampaignRepository.VILLAIN_DRAW_ID,
            ).firstOrNull()
            val stages = tracker.villains[drawn] ?: tracker.villains[scenarioId]
            val scheme = tracker.schemes[scenarioId]
            if (stages != null || scheme != null) {
                return EncounterSetup(
                    villain = stages?.map { it.toSide() }.orEmpty(),
                    scheme = scheme?.map { it.toSide() }.orEmpty(),
                    players = run.state.heroes.size.coerceAtLeast(1),
                )
            }
        }
        // The template names the villain in every campaign but one. Fear No
        // Evil deals a subordinate per job instead, so there the answer is in
        // the log rather than the template, and reading only the template left
        // the tracker permanently blank for that whole campaign.
        val villain = scenario.baseSetup
            ?.villainStages(run.state.difficulty, null)
            ?.firstOrNull()
            ?: CampaignEngine.drawnCards(
                run.state,
                scenarioId,
                CampaignRepository.VILLAIN_DRAW_ID,
            ).firstOrNull()
            ?: return EncounterSetup()
        val locale = preferences.currentCardLocale()
        val setCode = cardDao.getCardsByCodes(listOf(villain))
            .firstOrNull { it.locale == locale.code }
            ?.cardSetCode
            ?: return EncounterSetup()
        // The campaigns use the game's own word for the harder mode, and an
        // expert campaign plays the same later stages a one-off expert game
        // does.
        return encounterRepository.setupFor(
            scenarioCode = setCode,
            players = run.state.heroes.size.coerceAtLeast(1),
            expert = run.state.difficulty.equals("expert", ignoreCase = true),
        )
    }

    /**
     * The counters this run was put away with, if it was.
     *
     * Null when there is no saved game for this run, or when it was saved with
     * the tracker off, or when the stored text will not parse. A break that
     * cannot be read back starts fresh, which is what happened to every break
     * before these numbers were stored at all.
     */
    private suspend fun pausedProgressFor(runId: String): EncounterProgress? {
        val saved = pausedGameDao.current()?.takeIf { it.campaignRunId == runId } ?: return null
        val text = saved.encounterProgress.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            json.decodeFromString(EncounterProgress.serializer(), text)
        }.getOrNull()
    }

    private fun updateEncounter(transform: Encounter.() -> Encounter) {
        state.value = state.value.copy(encounter = state.value.encounter.transform())
    }

    fun damageVillain(amount: Int) = updateEncounter { damaged(amount) }

    fun changeThreat(amount: Int) = updateEncounter { threatened(amount) }

    fun advanceVillain() = updateEncounter { villainAdvanced() }

    fun advanceScheme() = updateEncounter { schemeAdvanced() }

    fun endRound() = updateEncounter { roundEnded() }

    fun setKeepAwake(value: Boolean) {
        state.value = state.value.copy(keepAwake = value)
    }

    /** "I'm ready" — starts the clock and moves to the play page. */
    fun beginScenario() {
        val id = runId ?: return
        val run = state.value.run ?: return
        viewModelScope.launch {
            // The clock the run already has, not a new one. A scenario put
            // away on a long break and picked up again was starting from zero,
            // which threw away everything already played.
            repository.updateTimer(
                id,
                run.timer.start(System.currentTimeMillis()),
                run.state.currentScenarioId,
            )
            reload()
            // Built here rather than on load: this is the moment the table is
            // laid out, so the stages and the player count are settled.
            val run = state.value.run
            val setup = if (run != null) buildEncounter(run) else EncounterSetup()
            // A scenario put away on a long break comes back where it stood.
            // Without this the counters started again from full health, which
            // made the break worth less than a photograph of the table.
            val resumed = run?.let { pausedProgressFor(it.entity.id) }
            state.value = state.value.copy(
                page = RunPage.PLAYING,
                trackEncounter = setup.isUsable,
                trackerWanted = preferences.trackEncounter.first(),
                encounter = if (resumed != null) {
                    Encounter(setup = setup, progress = resumed)
                } else {
                    Encounter.startOf(setup)
                },
            )
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
                summary = ScenarioOutcomeSummary(
                    victory = false,
                    elapsedMillis = elapsed,
                    victoryPoints = null,
                    // Resolved against the run as it was, because the draws
                    // belonging to a scenario do not outlive it.
                    outcomeMessage = outcomeMessage(run, scenarioId, victory = false),
                    scenarioId = scenarioId,
                    canContinue = allowsContinue(run, scenarioId),
                ),
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
                    outcomeMessage = outcomeMessage(run, scenarioId, victory = true),
                    scenarioId = scenarioId,
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

    /**
     * The closing line of the scenario just played, placeholders resolved.
     *
     * Read from the run as it stood before the result was filed: a scenario's
     * draws are cleared when it finishes, and the line usually names one.
     */
    /**
     * Whether the campaign says what continuing past this defeat costs.
     *
     * The effects are the rule: a campaign that has none has no such rule, and
     * the way out should not be offered.
     */
    private fun allowsContinue(run: CampaignRun, scenarioId: String): Boolean =
        run.template.scenarios.firstOrNull { it.id == scenarioId }
            ?.onDefeat?.onContinue.orEmpty().isNotEmpty()

    private fun outcomeMessage(
        run: CampaignRun,
        scenarioId: String,
        victory: Boolean,
    ): String? {
        val scenario = run.template.scenarios.firstOrNull { it.id == scenarioId } ?: return null
        val outcome = if (victory) scenario.onVictory else scenario.onDefeat
        val text = outcome?.message?.resolve(textLocale ?: run.localeCode)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return resolveDraws(text, run, scenarioId)
    }

    /**
     * Starts writing the scenario down so the table can be cleared.
     *
     * The clock stops with it. A game put away with the clock still running
     * records the washing up as play time.
     */
    fun beginLongBreak() {
        val run = state.value.run ?: return
        state.value = state.value.copy(
            longBreak = LongBreakDraft(
                heroLives = run.state.heroes.associate { it.heroCardCode to "" },
            ),
        )
        pauseTimer()
    }

    fun updateLongBreak(draft: LongBreakDraft) {
        state.value = state.value.copy(longBreak = draft)
    }

    fun cancelLongBreak() {
        state.value = state.value.copy(longBreak = null)
    }

    /**
     * Files the scenario away and leaves.
     *
     * Saved beside every other paused game rather than somewhere of its own, so
     * a player looking for the game they put away has one place to look. The
     * run id is what tells the Play screen to reopen the campaign rather than a
     * standalone session, since a campaign scenario has a log to go back to.
     */
    fun saveLongBreak(onSaved: () -> Unit) {
        val current = state.value
        val draft = current.longBreak ?: return
        val run = current.run ?: return
        val scenarioId = run.state.currentScenarioId ?: return
        val scenario = run.template.scenarios.firstOrNull { it.id == scenarioId }

        viewModelScope.launch {
            pausedGameDao.upsert(
                PausedGameEntity(
                    id = UUID.randomUUID().toString(),
                    savedAt = System.currentTimeMillis(),
                    scenarioCode = scenarioId,
                    scenarioName = scenario?.name?.resolve(run.localeCode) ?: scenarioId,
                    difficulty = run.state.difficulty.orEmpty(),
                    heroes = run.state.heroes.joinToString(",") {
                        "${it.heroCardCode}|${it.name}"
                    },
                    elapsedMillis = run.timer.elapsedAt(System.currentTimeMillis()),
                    phase = draft.phase.name,
                    villainStep = if (draft.phase == PausedPhase.VILLAIN) {
                        draft.villainStep.name
                    } else {
                        ""
                    },
                    heroLives = draft.heroLives.entries.joinToString(",") { (code, life) ->
                        "$code|${life.ifBlank { "?" }}"
                    },
                    // From the tracker when it was running, as a one-off game
                    // does it, so the recap shows real numbers the table never
                    // had to type.
                    villainLife = if (current.trackEncounter) {
                        current.encounter.villainHealth
                            ?.minus(current.encounter.progress.damage)
                            ?.coerceAtLeast(0)
                            ?: 0
                    } else {
                        draft.villainLife.toIntOrNull() ?: 0
                    },
                    villainStage = if (current.trackEncounter) {
                        current.encounter.progress.villainIndex + 1
                    } else {
                        draft.villainStage
                    },
                    campaignRunId = run.entity.id,
                    // Written by the app, for the reason the one-off game does
                    // it: the numbers are already on the screen, and asking the
                    // table to copy them out is the wrong way round.
                    encounterProgress = if (current.trackEncounter) {
                        json.encodeToString(
                            EncounterProgress.serializer(),
                            current.encounter.progress,
                        )
                    } else {
                        ""
                    },
                ),
            )
            state.value = state.value.copy(longBreak = null)
            onSaved()
        }
    }

    /**
     * Goes back to the scenario just lost, without settling anything.
     *
     * Fear No Evil says a lost job has not failed and may be played again, so
     * retrying costs the campaign nothing: the environment turns over only
     * when the players decide to move on instead.
     */
    fun retryScenario() {
        val id = runId ?: return
        val scenarioId = state.value.summary?.scenarioId ?: return
        viewModelScope.launch {
            repository.chooseScenario(id, scenarioId)
            reload()
            state.value = state.value.copy(summary = null, page = RunPage.BRIEFING)
        }
    }

    /** Moves on from a result, applying whatever that decision costs. */
    fun continueFromOutcome() {
        val id = runId ?: return
        val summary = state.value.summary
        val scenarioId = summary?.scenarioId
        viewModelScope.launch {
            if (scenarioId != null) {
                repository.continueFromOutcome(id, scenarioId, summary.victory)
                reload()
            }
            state.value = state.value.copy(summary = null, page = pageFor(state.value.run))
        }
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
