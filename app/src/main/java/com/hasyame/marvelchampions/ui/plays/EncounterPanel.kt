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
    /*
        The same, by track, for a scenario with several villains or several
        main schemes on the table at once. A screen with one of each need
        not know: the defaults hand track zero to the callbacks above.
    */
    onDamageVillainAt: (Int, Int) -> Unit = { _, amount -> onDamageVillain(amount) },
    onAdvanceVillainAt: (Int) -> Unit = { onAdvanceVillain() },
    onChangeThreatAt: (Int, Int, Int) -> Unit = { _, copy, amount -> onChangeThreat(copy, amount) },
    onAdvanceSchemeAt: (Int) -> Unit = { onAdvanceScheme() },
    onDamageStructure: (Int) -> Unit = {},
    onTurnStructure: () -> Unit = {},
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

            // One villain almost always. Tower Defense fields two at once, each
            // its own counter, and neither is defeated while the other still
            // stands: the turn to the next stage is offered once, below both,
            // when both are down, because the cards turn together.
            val linked = encounter.setup.villainsLinked
            for (track in 0 until encounter.villainTrackCount) {
                val villain = encounter.villainSideAt(track) ?: continue
                if (track > 0) {
                    HorizontalDivider()
                }
                CounterRow(
                    title = "${villain.name} ${villain.stage}".trim(),
                    value = encounter.villainDamageAt(track),
                    total = encounter.villainHealthAt(track),
                    unit = stringResource(R.string.session_damage),
                    unknown = villain.starred,
                    enabled = enabled,
                    onChange = { amount -> onDamageVillainAt(track, amount) },
                    reached = encounter.villainDefeatedAt(track),
                    advanceLabel = stringResource(R.string.session_flip_villain),
                    onAdvance = if (linked || encounter.isFinalVillainStageAt(track)) null else ({ onAdvanceVillainAt(track) }),
                    flavour = VitalFlavour.BLOOD,
                )
                if (linked && encounter.villainDownAt(track) && !encounter.villainDefeatedAt(track)) {
                    // At zero, and not done: the card says so, and so does this.
                    Text(
                        text = stringResource(R.string.session_villain_holds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (linked && encounter.villainDefeated && !encounter.isFinalVillainStage) {
                Button(
                    onClick = { onAdvanceVillainAt(0) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.session_flip_villains)) }
            }

            for (track in 0 until encounter.schemeTrackCount) {
                val scheme = encounter.schemeSideAt(track) ?: continue
                // Usually one, and then this reads exactly as it always did.
                // A scenario that deals a main scheme to each player gets one
                // counter each, named for whose it is, because they are
                // separate games of thwarting that finish at different times.
                val copies = if (track == 0) encounter.schemeCopies else 1
                repeat(copies) { index ->
                    HorizontalDivider()
                    CounterRow(
                        title = if (copies > 1) {
                            stringResource(R.string.session_scheme_player, scheme.name, index + 1)
                        } else {
                            "${scheme.name} ${scheme.stage}".trim()
                        },
                        value = encounter.threatOnTrack(track, index),
                        total = encounter.schemeLimitAt(track),
                        unit = stringResource(R.string.session_threat),
                        unknown = scheme.starred,
                        enabled = enabled,
                        onChange = { amount -> onChangeThreatAt(track, index, amount) },
                        reached = encounter.schemeCompleteAt(track, index),
                        // A scheme that clears rather than completes offers the
                        // clearing, at the limit, in the words of its card.
                        advanceLabel = stringResource(
                            if (encounter.setup.schemesReset) R.string.session_clear_scheme else R.string.session_advance_scheme,
                        ),
                        onAdvance = when {
                            encounter.setup.schemesReset -> ({ onAdvanceSchemeAt(track) })
                            encounter.isFinalSchemeStageAt(track) -> null
                            else -> ({ onAdvanceSchemeAt(track) })
                        },
                        flavour = VitalFlavour.ELECTRIC,
                    )
                }
            }

            // A card with hit points of its own: Avengers Tower. Turning it
            // over is offered when a side is full; the last side full is said,
            // not acted on, because losing is the table's to declare.
            encounter.structureSide?.let { side ->
                HorizontalDivider()
                CounterRow(
                    title = "${side.name} · ${side.stage}".trim(),
                    value = encounter.progress.structureDamage,
                    total = encounter.structureLimit,
                    unit = stringResource(R.string.session_structure_damage),
                    unknown = false,
                    enabled = enabled,
                    onChange = onDamageStructure,
                    reached = encounter.structureFull,
                    advanceLabel = stringResource(R.string.session_turn_structure),
                    onAdvance = if (encounter.isFinalStructureSide) null else onTurnStructure,
                    flavour = VitalFlavour.BLOOD,
                )
                if (encounter.structureLost) {
                    Text(
                        text = stringResource(R.string.session_structure_lost),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
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
