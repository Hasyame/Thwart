package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.play.Encounter
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
/**
 * Villain health and scheme threat, counted for the number of people playing.
 *
 * Only counters, deliberately. It does not know that a Crisis icon stops
 * thwarting or that a minion just entered play — a tracker that
 * half-adjudicates rules is wrong at somebody's table, and then the numbers it
 * *is* keeping stop being trusted either.
 */
@Composable
fun EncounterPanel(
    encounter: Encounter,
    enabled: Boolean,
    keepAwake: Boolean,
    onDamageVillain: (Int) -> Unit,
    /** Which copy of the main scheme, and by how much. */
    onChangeThreat: (Int, Int) -> Unit,
    onAdvanceVillain: () -> Unit,
    onAdvanceScheme: () -> Unit,
    onEndRound: () -> Unit,
    onKeepAwake: (Boolean) -> Unit,
) {

    ComicPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.session_round, encounter.progress.round),
                style = MaterialTheme.typography.titleSmall,
            )

            encounter.villainSide?.let { villain ->
                CounterRow(
                    title = "${villain.name} ${villain.stage}".trim(),
                    value = encounter.progress.damage,
                    total = encounter.villainHealth,
                    unit = stringResource(R.string.session_damage),
                    unknown = villain.starred,
                    enabled = enabled,
                    onChange = onDamageVillain,
                    reached = encounter.villainDefeated,
                    advanceLabel = stringResource(R.string.session_flip_villain),
                    onAdvance = if (encounter.isFinalVillainStage) null else onAdvanceVillain,
                    flavour = VitalFlavour.BLOOD,
                )
            }

            encounter.schemeSide?.let { scheme ->
                // Usually one, and then this reads exactly as it always did.
                // A scenario that deals a main scheme to each player gets one
                // counter each, named for whose it is, because they are
                // separate games of thwarting that finish at different times.
                repeat(encounter.schemeCopies) { index ->
                    HorizontalDivider()
                    CounterRow(
                        title = if (encounter.schemeCopies > 1) {
                            stringResource(R.string.session_scheme_player, scheme.name, index + 1)
                        } else {
                            scheme.name
                        },
                        value = encounter.threatOn(index),
                        total = encounter.schemeLimit,
                        unit = stringResource(R.string.session_threat),
                        unknown = scheme.starred,
                        enabled = enabled,
                        onChange = { amount -> onChangeThreat(index, amount) },
                        reached = encounter.schemeCompleteOn(index),
                        advanceLabel = stringResource(R.string.session_advance_scheme),
                        onAdvance = if (encounter.isFinalSchemeStage) null else onAdvanceScheme,
                        flavour = VitalFlavour.ELECTRIC,
                    )
                }
            }

            // The one piece of arithmetic worth automating: it is per player,
            // it happens every round, and forgetting it is the commonest way a
            // game drifts from where it should be.
            Button(
                onClick = onEndRound,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.session_end_round)) }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_keep_awake),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = keepAwake, onCheckedChange = onKeepAwake)
            }
        }
    }
}

/**
 * Steps the buttons offer.
 *
 * One for chip damage and the usual thwart, five because a hero hitting for
 * five is an ordinary turn and tapping +1 five times at a table is not.
 */
private val COUNTER_STEPS = listOf(-5, -1, 1, 5)

/** One counter: what it is, where it stands, and the buttons that move it. */
@Composable
private fun CounterRow(
    title: String,
    value: Int,
    total: Int?,
    unit: String,
    /** The card prints a star where the number goes, so nobody knows it yet. */
    unknown: Boolean,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    reached: Boolean,
    advanceLabel: String,
    onAdvance: (() -> Unit)?,
    flavour: VitalFlavour,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            // Three different things, and they must not look alike: a number to
            // count towards, a star meaning the scenario decides it, and no
            // limit at all — a stage like The Brotherhood Strikes! that ends
            // some other way, where a target would be a lie.
            text = when {
                total != null -> "$value / $total"
                unknown -> "$value / ★"
                else -> "$value"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (reached) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Read across a table by people who are not holding the phone, so the
        // bar carries where a digit changing does not.
        VitalBar(value = value, total = total, flavour = flavour)
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COUNTER_STEPS.forEach { step ->
                OutlinedButton(
                    onClick = { onChange(step) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) { Text(if (step > 0) "+$step" else "$step") }
            }
        }
        if (reached && onAdvance != null) {
            Button(
                onClick = onAdvance,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(advanceLabel) }
        }
    }
}
