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
import com.hasyame.marvelchampions.data.photos.PhotoStore
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
    draft: LongBreakDraft,
    /** Hero code to the name to show, in seat order. */
    heroes: List<Pair<String, String>>,
    /**
     * Whether the tracker was counting this game.
     *
     * When it was, the villain's health and which card is face up are already
     * known and are not asked for. Asking the table to copy a number off the
     * screen it is looking at is the wrong way round, and a second copy of a
     * figure is a second chance to disagree with it.
     */
    tracked: Boolean,
    photos: List<String>,
    photoStore: PhotoStore,
    onDraft: (LongBreakDraft) -> Unit,
    onPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val takePhoto = rememberTablePhotoCapture(
        photoStore = photoStore,
        scope = scope,
        onTaken = onPhoto,
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
                taken = photos.size,
                onTake = takePhoto,
                modifier = Modifier.fillMaxWidth(),
            )
            TablePhotoStrip(
                names = photos,
                photoStore = photoStore,
                onOpen = { },
                onDelete = onRemovePhoto,
            )
        }

        Section(stringResource(R.string.long_break_where)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PausedPhase.entries.forEach { phase ->
                    FilterChip(
                        selected = draft.phase == phase,
                        onClick = { onDraft(draft.copy(phase = phase)) },
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
                                onDraft(draft.copy(villainStep = step))
                            },
                            label = { Text(stringResource(step.label)) },
                        )
                    }
                }
            }
        }

        Section(stringResource(R.string.long_break_heroes)) {
            heroes.forEach { (heroCode, name) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = draft.heroLives[heroCode].orEmpty(),
                        onValueChange = { typed ->
                            onDraft(
                                draft.copy(
                                    heroLives = draft.heroLives +
                                        (heroCode to typed.filter(Char::isDigit)),
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
            if (tracked) {
                Text(
                    text = stringResource(R.string.long_break_villain_tracked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = draft.villainLife,
                    onValueChange = {
                        onDraft(
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
                            onClick = { onDraft(draft.copy(villainStage = stage)) },
                            label = { Text(stageLabel(stage)) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.long_break_save)) }

        OutlinedButton(
            onClick = onCancel,
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
