package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.data.db.entity.PausedPhase
import com.hasyame.marvelchampions.data.db.entity.VillainStep
import com.hasyame.marvelchampions.ui.photos.TablePhotoButton
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import com.hasyame.marvelchampions.ui.photos.rememberTablePhotoCapture

/**
 * Putting a game away for longer than a pause.
 *
 * A short break stops the clock and nothing else. This is for the table being
 * cleared, or left for a week, where the clock is the least of it: what matters
 * is where everything stood. So the page asks the four things that are hard to
 * reconstruct and easy to answer while the table is still in front of you.
 *
 * Every field is optional on purpose. A table putting a game away in a hurry
 * should not be held up by a form, and a photograph on its own is usually
 * enough to rebuild from.
 */
@Composable
fun LongBreakPage(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.longBreak ?: return
    val scope = rememberCoroutineScope()
    val takePhoto = rememberTablePhotoCapture(
        photoStore = viewModel.photoStore,
        scope = scope,
        onTaken = viewModel::addPhoto,
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.long_break_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.long_break_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The photograph first: it is the one thing that captures what no list
        // of fields can, and the table is still standing while it is taken.
        Section(stringResource(R.string.long_break_photo)) {
            TablePhotoButton(
                taken = state.photos.size,
                onTake = takePhoto,
                modifier = Modifier.fillMaxWidth(),
            )
            TablePhotoStrip(
                names = state.photos,
                photoStore = viewModel.photoStore,
                onOpen = { },
                onDelete = viewModel::removePhoto,
            )
        }

        Section(stringResource(R.string.long_break_where)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PausedPhase.entries.forEach { phase ->
                    FilterChip(
                        selected = draft.phase == phase,
                        onClick = { viewModel.updateLongBreak(draft.copy(phase = phase)) },
                        label = { Text(stringResource(phase.label)) },
                    )
                }
            }
            // The villain phase has steps and the player phase does not, so the
            // steps only appear once they are the question being asked.
            if (draft.phase == PausedPhase.VILLAIN) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VillainStep.entries.forEach { step ->
                        FilterChip(
                            selected = draft.villainStep == step,
                            onClick = {
                                viewModel.updateLongBreak(draft.copy(villainStep = step))
                            },
                            label = { Text(stringResource(step.label)) },
                        )
                    }
                }
            }
        }

        Section(stringResource(R.string.long_break_heroes)) {
            state.heroes.forEach { hero ->
                val name = state.names.heroes[hero.heroCode] ?: hero.heroCode
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = draft.heroLives[hero.heroCode].orEmpty(),
                        onValueChange = { typed ->
                            viewModel.updateLongBreak(
                                draft.copy(
                                    heroLives = draft.heroLives +
                                        (hero.heroCode to typed.filter(Char::isDigit)),
                                ),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.long_break_life)) },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }

        Section(stringResource(R.string.long_break_villain)) {
            OutlinedTextField(
                value = draft.villainLife,
                onValueChange = {
                    viewModel.updateLongBreak(
                        draft.copy(villainLife = it.filter(Char::isDigit)),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.long_break_life)) },
                modifier = Modifier.width(160.dp),
            )
            Text(
                text = stringResource(R.string.long_break_stage),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { stage ->
                    FilterChip(
                        selected = draft.villainStage == stage,
                        onClick = { viewModel.updateLongBreak(draft.copy(villainStage = stage)) },
                        label = { Text(stageLabel(stage)) },
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.saveLongBreak(onSaved) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.long_break_save)) }

        OutlinedButton(
            onClick = viewModel::cancelLongBreak,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.long_break_back)) }
    }
}

/** I, II or III, which is how the card itself is labelled. */
private fun stageLabel(stage: Int): String = "I".repeat(stage)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ComicPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

private val PausedPhase.label: Int
    get() = when (this) {
        PausedPhase.PLAYER -> R.string.phase_player
        PausedPhase.VILLAIN -> R.string.phase_villain
    }

private val VillainStep.label: Int
    get() = when (this) {
        VillainStep.PLACE_THREAT -> R.string.villain_step_threat
        VillainStep.ACTIVATE_MINIONS -> R.string.villain_step_minions
        VillainStep.DEAL_ENCOUNTERS -> R.string.villain_step_deal
        VillainStep.REVEAL_ENCOUNTERS -> R.string.villain_step_reveal
        VillainStep.PASS_FIRST_PLAYER -> R.string.villain_step_pass
    }
