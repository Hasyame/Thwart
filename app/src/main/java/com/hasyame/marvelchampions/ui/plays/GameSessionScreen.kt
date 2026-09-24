package com.hasyame.marvelchampions.ui.plays

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.hasyame.marvelchampions.core.designsystem.component.ComicTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.ratings.ofPlay
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.play.FearNoEvil
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.ratings.RatingSubject
import com.hasyame.marvelchampions.ui.achievements.UnlockedAchievements
import com.hasyame.marvelchampions.ui.photos.TablePhotoButton
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import com.hasyame.marvelchampions.ui.photos.rememberTablePhotoCapture
import com.hasyame.marvelchampions.ui.ratings.RatingBadge
import com.hasyame.marvelchampions.ui.ratings.RatingPanel
import com.hasyame.marvelchampions.ui.util.ChoiceOption
import com.hasyame.marvelchampions.ui.util.ChooseValueDialog
import com.hasyame.marvelchampions.ui.util.KeepScreenOn
import com.hasyame.marvelchampions.ui.util.aspectLabel
import com.hasyame.marvelchampions.ui.util.labelRes
import kotlinx.coroutines.delay

/**
 * Set up a game yourself, then have the app time it.
 *
 * The randomiser answers "what shall I play"; this answers "I already know what
 * I am playing, record it properly". They are different acts, so this is its
 * own screen rather than a mode of the draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSessionScreen(
    challengeJson: String? = null,
    deckIds: String = "",
    randomScenario: Boolean = false,
    onBack: () -> Unit,
    scenarioCode: String? = null,
    difficulty: String? = null,
    heroes: String? = null,
    modularSets: String? = null,
    standardSet: String? = null,
    autoStart: Boolean = false,
    resumeId: String? = null,
    replayId: String? = null,
    onOpenPlays: () -> Unit,
    onOpenAchievements: () -> Unit,
    viewModel: GameSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()

    /*
     * The result of a filed game belongs to this screen rather than to the
     * session, which ended when the game was written down. Leaving for
     * another tab therefore puts it away, and coming back to Play offers a
     * new game instead of the one just finished; the rating and the
     * BoardGameGeek entry are on the game's row in the history.
     *
     * "Left" means this destination stopped while the app stayed in front.
     * Turning the phone or putting the app away stops it too, and those keep
     * the result, because the player has not gone anywhere.
     */
    val host = LocalContext.current.findActivity()
    val destination = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(destination, host) {
        val watcher = LifecycleEventObserver { _, event ->
            val inForeground = host?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
            if (event == Lifecycle.Event.ON_STOP && inForeground) {
                viewModel.dismissRecorded()
            }
        }
        destination.addObserver(watcher)
        onDispose { destination.removeObserver(watcher) }
    }

    // Leaving mid-game throws away a running clock and everything set up, so
    // it asks first. Only while playing: backing out of the setup loses nothing
    // worth confirming.
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = {
        if (state.phase == SessionPhase.PLAYING) confirmLeave = true else onBack()
    }

    // A draw handed over from the randomiser, applied once.
    LaunchedEffect(scenarioCode, difficulty, heroes, modularSets, standardSet, resumeId, replayId) {
        // The three are exclusive. A paused game or a game being played again
        // brings its own scenario, heroes and difficulty, and taking half
        // from the route is how they disagree.
        if (challengeJson != null) {
            viewModel.prefillChallenge(kotlinx.serialization.json.Json.decodeFromString(challengeJson))
        } else if (deckIds.isNotBlank()) {
            viewModel.prefillDecks(deckIds.split(','), randomScenario, scenarioCode, difficulty, modularSets, standardSet, autoStart)
        } else if (resumeId != null) {
            viewModel.resume(resumeId)
        } else if (replayId != null) {
            viewModel.replay(replayId)
        } else {
            viewModel.prefill(
                scenarioCode = scenarioCode,
                difficulty = difficulty,
                heroes = heroes,
                modularSets = modularSets,
                standardSet = standardSet,
                autoStart = autoStart,
            )
        }
    }

    // Only while the clock is actually running, so a paused or finished game
    // is not waking the composition once a second for nothing.
    LaunchedEffect(state.phase, state.timer.isRunning) {
        while (state.phase == SessionPhase.PLAYING && state.timer.isRunning) {
            viewModel.tick()
            delay(TICK_MILLIS)
        }
    }

    Scaffold(
        topBar = {
            ComicTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.session_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.phase == SessionPhase.SETUP -> SetupPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )

            state.phase == SessionPhase.BRIEFING -> BriefingPhase(
                state = state,
                onPlay = viewModel::beginPlaying,
                onBack = viewModel::backToSetup,
                modifier = Modifier.padding(padding),
            )

            // Ahead of the playing page rather than a phase of its own: the game
            // has not moved on, it is being written down, and cancelling puts
            // the table straight back.
            state.longBreak != null -> LongBreakPage(
                // Read into a local first: `state` is a delegated property, so
                // the compiler cannot know the field is still non-null here.
                draft = requireNotNull(state.longBreak),
                // displayName, not the name table alone. A deck carries its
                // own hero name and the table is only a fallback, so looking in
                // the table first showed the card code for anyone whose hero the
                // table did not happen to hold.
                heroes = state.heroes.map {
                    it.heroCode to it.displayName(state.names.heroes)
                },
                tracked = state.trackEncounter,
                photos = state.photos,
                photoStore = viewModel.photoStore,
                onDraft = viewModel::updateLongBreak,
                onPhoto = viewModel::addPhoto,
                onRemovePhoto = viewModel::removePhoto,
                onCancel = viewModel::cancelLongBreak,
                onSave = { viewModel.saveLongBreak(onBack) },
                modifier = Modifier.padding(padding),
            )

            else -> PlayingPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.session_leave_title)) },
            text = { Text(stringResource(R.string.session_leave_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.session_leave_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.session_leave_no))
                }
            },
        )
    }

    recorded?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecorded,
            title = { Text(stringResource(R.string.session_saved_title)) },
            // Three equal choices, so they live in the body as full-width
            // buttons. A dialog's confirm slot is pinned to the right and
            // sized to its content, which left a stack of three squashed
            // against the right edge.
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when (outcome) {
                            is PlayRecorded.SavedOnly -> stringResource(R.string.session_saved)
                            is PlayRecorded.SavedAndReported ->
                                stringResource(R.string.session_saved_sent)

                            is PlayRecorded.SavedAskToReport ->
                                stringResource(R.string.session_saved_can_send)

                            is PlayRecorded.SavedReportFailed ->
                                stringResource(R.string.session_saved_not_sent, outcome.detail)
                        },
                    )

                    // What this game earned, before the ratings: the one
                    // moment somebody wants to hear it.
                    UnlockedAchievements(
                        unlocked = state.unlocked,
                        onOpen = {
                            viewModel.newGame()
                            onOpenAchievements()
                        },
                    )

                    // Optional, after the result is saved, never before and
                    // never as a step: the scenario, then each modular set
                    // that was on the table.
                    state.lastPlay?.let { played ->
                        RatingPanel(
                            title = stringResource(R.string.rating_title),
                            subjects = RatingSubject.ofPlay(played),
                            own = state.ownRatings,
                            labelOf = { subject ->
                                when (subject.kind) {
                                    RatingSubject.Kind.SCENARIO -> state.names.scenarios[subject.code]
                                    else -> state.names.modularSets[subject.code]
                                } ?: subject.code
                            },
                            onRate = viewModel::rate,
                        )
                    }

                    // The same game again, which is what a table usually
                    // wants next, and an empty setup beside it.
                    if (state.lastTable != null) {
                        Button(
                            onClick = viewModel::playAgain,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.session_play_again)) }
                    }

                    if (state.lastTable == null) {
                        Button(
                            onClick = viewModel::newGame,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.session_another)) }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::newGame,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.session_another)) }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.newGame()
                            onOpenPlays()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_see_stats)) }

                    OutlinedButton(
                        onClick = {
                            viewModel.newGame()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.session_back_to_menu)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.newGame(); onBack() }) {
                    Text(stringResource(R.string.session_back_to_menu))
                }
            },
        )
    }
}
@Composable
private fun SetupPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    // Which long list is being picked from, if any. Scenarios and modular sets
    // used to be rows of chips, which is fine at ten and unusable at eighty.
    var choosing by remember { mutableStateOf<SetupPicker?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.challengeUnavailable) {
            Text(stringResource(R.string.achievement_setup_unavailable), color = MaterialTheme.colorScheme.error)
        }
        // Where this table came from, when it is the one just played. Said
        // once, because the second sentence of it is "change what you like".
        if (state.playAgainNote) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.session_play_again_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = viewModel::dismissPlayAgainNote,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.action_got_it)) }
                }
            }
        }
        // Same note as the randomiser, for the same reason: everything on this
        // screen is filtered by the collection, and a player who does not know
        // that concludes something is missing.
        Text(
            text = stringResource(R.string.randomizer_collection_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The order follows how a table decides: how hard, then what we are
        // playing, then what goes in the deck, then who is playing it.
        PickerSection(stringResource(R.string.session_main_difficulty)) {
            // The same rule as the randomiser: a difficulty is a set of
            // encounter cards that came in a box, so only the ones the
            // collection can field are offered.
            state.pools.difficulties.forEach { difficulty ->
                val stored = difficulty.name.lowercase()
                FilterChip(
                    selected = state.difficulty == stored,
                    onClick = { viewModel.setDifficulty(stored) },
                    label = { Text(stringResource(difficulty.labelRes())) },
                )
            }
        }

        // An Expert set is shuffled in with a Standard set, never on its own,
        // so choosing Expert leaves a second question. It only appears then,
        // because for a Standard difficulty there is nothing to ask.
        if (state.isExpertDifficulty) {
            val standards = state.pools.difficulties.filter { it.isStandard }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PickerSection(stringResource(R.string.session_standard_set)) {
                    standards.forEach { difficulty ->
                        val stored = difficulty.name.lowercase()
                        FilterChip(
                            selected = state.standardSet == stored,
                            onClick = { viewModel.setStandardSet(stored) },
                            label = { Text(stringResource(difficulty.labelRes())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.session_standard_set_hint),
                    style = MaterialTheme.typography.bodySmall,
                    // Turns to the error colour while unanswered, because the
                    // start button is disabled and the reason must be visible.
                    color = if (state.standardSet == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        ChosenRow(
            label = stringResource(R.string.session_scenario),
            value = state.scenarioCode?.let { state.names.scenarios[it] ?: it },
            onClick = { choosing = SetupPicker.SCENARIO },
        )
        // A Fear No Evil job is played against one of the box's subordinates,
        // chosen here: the scenario row names the pair, and this row is where
        // the villain half is changed.
        val fneJob = state.scenarioCode?.let { FearNoEvil.split(it).first }
        if (fneJob != null && state.pools.villainChoices[fneJob].orEmpty().isNotEmpty()) {
            ChosenRow(
                label = stringResource(R.string.session_villain),
                value = FearNoEvil.split(state.scenarioCode.orEmpty()).second?.let { state.names.villains[it] ?: it },
                onClick = { choosing = SetupPicker.VILLAIN },
            )
        }
        // The average beside the choice, never beside the question.
        state.scenarioCode?.let { code ->
            val key = RatingSubject.scenario(code).key
            RatingBadge(summary = state.ratingSummaries[key], own = state.ownRatings[key])
        }

        ChosenRow(
            label = stringResource(R.string.session_modular_sets),
            value = state.modularSetCodes
                .takeIf { it.isNotEmpty() }
                ?.map { state.names.modularSets[it] ?: it }
                ?.joinToString(", "),
            onClick = { choosing = SetupPicker.MODULAR_SETS },
        )
        state.scenarioCode?.let { scenario ->
            state.modularSetCodes.forEach { set ->
                val summary = viewModel.summaryForSet(set)
                val own = state.ownRatings[RatingSubject.modular(set, scenario).key]
                if (summary != null || own != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.names.modularSets[set] ?: set,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        RatingBadge(summary = summary, own = own)
                    }
                }
            }
        }

        // Decks rather than heroes: a seat at this table is a deck somebody
        // built, and asking for a hero and an aspect separately made the player
        // describe a deck they already have.
        Text(
            text = stringResource(R.string.session_decks),
            style = MaterialTheme.typography.labelLarge,
        )
        if (state.decks.isEmpty()) {
            Text(
                text = stringResource(R.string.session_no_decks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.decks.forEach { deck ->
                    FilterChip(
                        selected = state.heroes.any { it.deckId == deck.id },
                        onClick = { viewModel.addDeck(deck) },
                        label = { Text(deck.name) },
                    )
                }
            }
        }

        if (state.heroes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.session_table),
                style = MaterialTheme.typography.titleSmall,
            )
            state.heroes.forEachIndexed { index, hero ->
                val heroName = hero.displayName(state.names.heroes)
                val aspects = hero.aspect.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { aspectLabel(it) }
                    .joinToString(" / ")
                ListItem(
                    // The deck name is what the player recognises, so it leads
                    // when there is one. A seat from a randomiser draw has none.
                    headlineContent = { Text(hero.deckName ?: heroName) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                heroName.takeIf { hero.deckName != null },
                                aspects.takeIf { it.isNotEmpty() },
                            ).joinToString(" · "),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeHero(index) }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                )
            }
        }

        Button(
            onClick = viewModel::start,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.session_start)) }
    }

    when (choosing) {
        SetupPicker.SCENARIO -> ChooseValueDialog(
            title = stringResource(R.string.session_scenario),
            options = state.pools.scenarios.map {
                ChoiceOption(
                    id = it.code,
                    label = state.names.scenarios[it.code] ?: it.code,
                    detail = state.names.packs[it.packCode],
                )
            }.sortedBy { it.label },
            selected = listOfNotNull(state.scenarioCode),
            limit = 1,
            onDismiss = { choosing = null },
            onConfirm = { picked ->
                picked.firstOrNull()?.let(viewModel::setScenario)
                // A job of Fear No Evil is not a scenario until its villain is
                // chosen, so the question follows at once.
                choosing = picked.firstOrNull()
                    ?.takeIf { FearNoEvil.needsVillain(it, state.pools.villainChoices) }
                    ?.let { SetupPicker.VILLAIN }
            },
        )

        SetupPicker.VILLAIN -> {
            val job = state.scenarioCode?.let { FearNoEvil.split(it).first }.orEmpty()
            ChooseValueDialog(
                title = stringResource(R.string.session_villain),
                options = state.pools.villainChoices[job].orEmpty().map {
                    ChoiceOption(id = it, label = state.names.villains[it] ?: it, detail = null)
                }.sortedBy { it.label },
                selected = listOfNotNull(state.scenarioCode?.let { FearNoEvil.split(it).second }),
                limit = 1,
                onDismiss = { choosing = null },
                onConfirm = { picked ->
                    picked.firstOrNull()?.let { viewModel.setScenario(FearNoEvil.compose(job, it)) }
                    choosing = null
                },
            )
        }

        SetupPicker.MODULAR_SETS -> ChooseValueDialog(
            title = stringResource(R.string.session_modular_sets),
            options = state.pools.modularSets.map {
                ChoiceOption(
                    id = it.code,
                    label = state.names.modularSets[it.code] ?: it.code,
                    detail = state.names.packs[it.packCode],
                )
            }.sortedBy { it.label },
            selected = state.modularSetCodes,
            limit = Int.MAX_VALUE,
            onDismiss = { choosing = null },
            onConfirm = { picked ->
                viewModel.setModularSets(picked)
                choosing = null
            },
        )

        null -> Unit
    }
}

/** The long lists on the setup screen, each picked in a dialog of its own. */
private enum class SetupPicker { SCENARIO, VILLAIN, MODULAR_SETS }

/**
 * One decision, showing what it is currently set to.
 *
 * Reads as a settings row rather than a wall of chips, which is what makes a
 * complete collection usable: the answer is visible, and the list only appears
 * when you go looking for it.
 */
@Composable
private fun ChosenRow(label: String, value: String?, onClick: () -> Unit) {
    // A button rather than a tappable row. As a list item it read as text, and
    // a player had no reason to think the answer was behind it.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = value ?: stringResource(R.string.session_choose),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun PickerSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/**
 * What to put on the table, before the clock starts.
 *
 * The same two halves a campaign briefing has, minus the campaign's own steps
 * because there are none here: what to fetch out of the boxes, then the setup
 * printed on the scenario's own main scheme. The clock waits, because laying a
 * game out takes minutes that are not play.
 */
@Composable
private fun BriefingPhase(
    state: GameSessionUiState,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.campaign_pre_setup),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.scenarioCode?.let { code ->
                    BriefingRow(
                        label = stringResource(R.string.randomizer_scenario),
                        value = state.names.scenarios[code] ?: code,
                    )
                }
                state.briefing.schemeName?.let {
                    BriefingRow(
                        label = stringResource(R.string.campaign_main_scheme_label),
                        value = it,
                    )
                }
                // The difficulty and, for an Expert one, the Standard set it
                // is played with, on one line: at the table they are one pile
                // of encounter cards, and two rows made the Standard set look
                // optional once the game had already started.
                val difficultyNames = (
                    listOfNotNull(state.difficulty, state.standardSet)
                    ).mapNotNull { stored ->
                    Difficulty.entries.firstOrNull { it.name.lowercase() == stored }
                }.map { stringResource(it.labelRes()) }
                BriefingRow(
                    label = stringResource(R.string.randomizer_difficulty),
                    value = difficultyNames
                        .ifEmpty { listOf(stringResource(R.string.difficulty_standard_i)) }
                        .joinToString(" + "),
                )
                // The sets that were drawn, which is the part a player cannot
                // work out from the scenario alone.
                state.modularSetCodes.takeIf { it.isNotEmpty() }?.let { sets ->
                    BriefingRow(
                        label = stringResource(R.string.campaign_encounter_sets_label),
                        value = sets.joinToString(", ") { state.names.modularSets[it] ?: it },
                    )
                }
                // What the table wrote down when they stopped. This is the
                // reason the pause was worth taking, and it is printed with the
                // rest of the setup because rebuilding the board is the setup.
                state.resumedFrom?.let { saved ->
                    BriefingRow(
                        label = stringResource(R.string.paused_game_stopped_at),
                        value = stringResource(R.string.paused_round, saved.villainStage),
                    )
                    if (saved.villainLife > 0) {
                        BriefingRow(
                            label = stringResource(R.string.paused_villain_life),
                            value = saved.villainLife.toString(),
                        )
                    }
                    saved.heroLives.split(",").filter { it.isNotBlank() }.forEach { entry ->
                        val parts = entry.split("|")
                        BriefingRow(
                            label = parts.getOrNull(0).orEmpty(),
                            value = parts.getOrNull(1).orEmpty(),
                        )
                    }
                }

                state.heroes.takeIf { it.isNotEmpty() }?.let { heroes ->
                    BriefingRow(
                        label = stringResource(R.string.randomizer_heroes),
                        value = heroes.map { hero ->
                            val aspects = hero.aspect.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { aspectLabel(it) }
                                .joinToString(" / ")
                            hero.displayName(state.names.heroes) + " · " + aspects
                        }.joinToString(", "),
                    )
                }
            }
        }

        // Absent for the two scenarios that keep their setup in the rules
        // insert. Nothing is shown rather than a guess.
        state.briefing.steps.takeIf { it.isNotEmpty() }?.let { steps ->
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.campaign_scheme_setup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    steps.forEach { step -> Text("• $step") }
                }
            }
        }

        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_play))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_change_setup))
        }
    }
}

@Composable
private fun BriefingRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlayingPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var correctingTime by remember { mutableStateOf(false) }

    if (correctingTime) {
        CorrectTimeDialog(
            elapsedMillis = state.elapsedMillis,
            onDismiss = { correctingTime = false },
            onConfirm = {
                viewModel.setElapsed(it)
                correctingTime = false
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.session_discard_title)) },
            text = { Text(stringResource(R.string.session_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discard()
                    },
                ) { Text(stringResource(R.string.session_discard_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.session_leave_no))
                }
            },
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .halftone(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Scrollable since the tracker joined it. The clock alone fits any
            // screen, but the counters underneath do not, and without this the
            // end-of-round button sat behind the navigation bar where nobody
            // could reach it.
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // The scenario is the headline of the whole screen: it is what the
            // table is playing, and it should read like a cover, not like a
            // caption. Big lettered type, centred, in the accent — but still
            // the plain scenario name, because somebody glancing down mid-game
            // needs to read it rather than admire it. The burst behind it is
            // deliberately not used: it belongs to the victory screen, and it
            // only works there because it appears nowhere else.
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = state.scenarioCode
                            ?.let { state.names.scenarios[it] ?: it }
                            .orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = state.heroes.joinToString(" · ") {
                            it.displayName(state.names.heroes)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (state.modularSetCodes.isNotEmpty()) {
                        Text(
                            text = state.modularSetCodes
                                .map { state.names.modularSets[it] ?: it }
                                .sorted()
                                .joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Only at a table with more than one player, and drawn once when
            // the game started rather than here — a value computed during
            // composition would pick a new player on every recomposition, so
            // the clock ticking would keep changing who goes first.
            state.firstPlayerIndex?.let { index ->
                val hero = state.heroes.getOrNull(index)
                if (hero != null) {
                    Text(
                        text = stringResource(
                            R.string.session_first_player,
                            hero.displayName(state.names.heroes),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // The clock is the control. Tapping it corrects the time, which is
            // where anyone would look for that.
            Text(
                text = TimerState.format(state.elapsedMillis),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.clickable(enabled = !state.isFinishing) {
                    correctingTime = true
                },
            )
            Text(
                text = stringResource(R.string.session_tap_to_correct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = if (state.timer.isRunning) viewModel::pause else viewModel::resume,
                enabled = !state.isFinishing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.timer.isRunning) {
                            R.string.session_pause
                        } else {
                            R.string.session_resume
                        },
                    ),
                )
            }

            // The other kind of stopping: not the clock, the table. Beside the
            // pause rather than replacing it, because most breaks are five
            // minutes and do not want a form.
            OutlinedButton(
                onClick = viewModel::beginLongBreak,
                enabled = !state.isFinishing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.session_long_break)) }

            // A photograph of the table, taken with the phone's own camera.
            // Kept with the game and filed with it when it ends.
            val scope = rememberCoroutineScope()
            val takePhoto = rememberTablePhotoCapture(
                photoStore = viewModel.photoStore,
                scope = scope,
                onTaken = viewModel::addPhoto,
            )
            TablePhotoButton(
                taken = state.photos.size,
                onTake = takePhoto,
                modifier = Modifier.fillMaxWidth(),
            )
            TablePhotoStrip(
                names = state.photos,
                photoStore = viewModel.photoStore,
                onDelete = viewModel::removePhoto,
            )

            if (state.trackEncounter && state.encounter.setup.isUsable) {
                if (state.keepAwake) {
                    KeepScreenOn()
                }
                EncounterPanel(
                    encounter = state.encounter,
                    enabled = !state.isFinishing,
                    keepAwake = state.keepAwake,
                    onDamageVillain = viewModel::damageVillain,
                    onChangeThreat = viewModel::changeThreat,
                    onAdvanceVillain = viewModel::advanceVillain,
                    onAdvanceScheme = viewModel::advanceScheme,
                    onEndRound = viewModel::endRound,
                    onKeepAwake = viewModel::setKeepAwake,
                    onDamageVillainAt = viewModel::damageVillainAt,
                    onAdvanceVillainAt = viewModel::advanceVillainAt,
                    onChangeThreatAt = viewModel::changeThreatAt,
                    onAdvanceSchemeAt = viewModel::advanceSchemeAt,
                    onDamageStructure = viewModel::damageStructure,
                    onTurnStructure = viewModel::turnStructure,
                )
            }

            // Both go dead the instant either is tapped, and say why. Without
            // this the screen looked unchanged while the play was saved and
            // sent, which read as "it did not register" and invited another tap.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.finish(won = true) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_won)) }
                OutlinedButton(
                    onClick = { viewModel.finish(won = false) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_lost)) }
            }

            TextButton(
                onClick = { confirmDiscard = true },
                enabled = !state.isFinishing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.session_discard)) }

            if (state.isFinishing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.session_saving),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private const val TICK_MILLIS = 1_000L

/**
 * The activity this composition is in, through whatever context wrappers
 * Compose hands over. Used to tell a tab change from the whole app being put
 * away: both stop this destination, only one of them means the player left.
 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
