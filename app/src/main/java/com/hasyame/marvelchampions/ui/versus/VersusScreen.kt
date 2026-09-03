package com.hasyame.marvelchampions.ui.versus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.ui.plays.EncounterPanel

/**
 * A competitive Civil War game, both boards on one device.
 *
 * Two teams build a scenario each, trade them, and race. What matters at the
 * table is which side is further along, so both boards are on one screen: the
 * question the mode asks cannot be answered while each half is on a different
 * phone.
 */
@Composable
fun VersusScreen(
    onBack: () -> Unit,
    viewModel: VersusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.phase) {
            VersusPhase.SETUP -> SetupPage(state, viewModel, onBack)
            VersusPhase.PLAYING -> PlayingPage(state, viewModel)
            VersusPhase.RESULT -> ResultPage(state, viewModel, onBack)
        }
    }
}

/**
 * What each team is facing.
 *
 * Asked rather than derived, twice over: a team faces the leader the *other*
 * team built, and the schemes are their pick out of the four their side offers.
 * Neither is knowable from the other.
 */
@Composable
private fun SetupPage(
    state: VersusUiState,
    viewModel: VersusViewModel,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(R.string.versus_setup_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.versus_setup_intro),
        style = MaterialTheme.typography.bodyMedium,
    )

    Team.entries.forEach { team ->
        val board = state.boards.getValue(team)
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = state.sides[team]?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.versus_faces),
                    style = MaterialTheme.typography.bodySmall,
                )

                ChipRow(
                    options = state.leaders[team].orEmpty(),
                    selected = board.leader,
                    onSelect = { viewModel.chooseLeader(team, it) },
                )
                HorizontalDivider()
                ChipRow(
                    options = state.schemesOne[team].orEmpty(),
                    selected = board.stageOne,
                    onSelect = { viewModel.chooseStageOne(team, it) },
                )
                ChipRow(
                    options = state.schemesTwo[team].orEmpty(),
                    selected = board.stageTwo,
                    onSelect = { viewModel.chooseStageTwo(team, it) },
                )

                Text(
                    text = stringResource(R.string.versus_players, board.heroes),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { count ->
                        FilterChip(
                            selected = board.heroes == count,
                            onClick = { viewModel.setHeroes(team, count) },
                            label = { Text("$count") },
                        )
                    }
                }
            }
        }
    }

    Button(
        onClick = viewModel::start,
        enabled = state.canStart,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.versus_start)) }

    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_close))
    }
}

/** One row of choices, wrapping is left to the caller's column width. */
@Composable
private fun ChipRow(
    options: List<VersusOption>,
    selected: VersusOption?,
    onSelect: (VersusOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    FilterChip(
                        selected = selected?.code == option.code,
                        onClick = { onSelect(option) },
                        label = { Text(option.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Both boards, and one button that ends the round on each.
 *
 * The round is shared deliberately. A game does not end until both sides have
 * played the same number of phases, and letting each board advance on its own
 * is exactly the drift the tie rules exist to prevent.
 */
@Composable
private fun PlayingPage(state: VersusUiState, viewModel: VersusViewModel) {
    Team.entries.forEach { team ->
        val board = state.boards.getValue(team)
        Text(
            text = state.sides[team]?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
        )
        EncounterPanel(
            encounter = board.encounter,
            enabled = true,
            keepAwake = true,
            onDamageVillain = { viewModel.damageLeader(team, it) },
            // One scheme a side here, so which copy is always the first.
            onChangeThreat = { _, amount -> viewModel.changeThreat(team, amount) },
            onAdvanceVillain = { viewModel.advanceLeader(team) },
            onAdvanceScheme = { viewModel.advanceScheme(team) },
            onEndRound = viewModel::endRound,
            onKeepAwake = { },
        )
    }

    Button(onClick = viewModel::endRound, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.versus_end_round))
    }

    HorizontalDivider()
    Text(
        text = stringResource(R.string.versus_who_won),
        style = MaterialTheme.typography.titleSmall,
    )
    Team.entries.forEach { team ->
        OutlinedButton(
            onClick = {
                viewModel.declare(
                    if (team == Team.REGISTRATION) {
                        VersusOutcome.REGISTRATION_WON
                    } else {
                        VersusOutcome.RESISTANCE_WON
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(state.sides[team]?.name.orEmpty()) }
    }
    OutlinedButton(
        onClick = { viewModel.declare(VersusOutcome.TIE) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.versus_tie)) }
}

/**
 * The result, and the tie-break list when it is needed.
 *
 * The five conditions are printed in order because they are applied in order,
 * and a table reaching for them has already had a long game.
 */
@Composable
private fun ResultPage(
    state: VersusUiState,
    viewModel: VersusViewModel,
    onBack: () -> Unit,
) {
    val winner = when (state.outcome) {
        VersusOutcome.REGISTRATION_WON -> state.sides[Team.REGISTRATION]?.name.orEmpty()
        VersusOutcome.RESISTANCE_WON -> state.sides[Team.RESISTANCE]?.name.orEmpty()
        else -> null
    }

    Text(
        text = winner ?: stringResource(R.string.versus_tie),
        style = MaterialTheme.typography.headlineSmall,
    )

    if (state.outcome == VersusOutcome.TIE) {
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.versus_tiebreak_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                listOf(
                    R.string.versus_tiebreak_1,
                    R.string.versus_tiebreak_2,
                    R.string.versus_tiebreak_3,
                    R.string.versus_tiebreak_4,
                    R.string.versus_tiebreak_5,
                ).forEachIndexed { index, line ->
                    Text(
                        text = "${index + 1}. ${stringResource(line)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    Button(onClick = viewModel::backToSetup, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.versus_again))
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_close))
    }
}
