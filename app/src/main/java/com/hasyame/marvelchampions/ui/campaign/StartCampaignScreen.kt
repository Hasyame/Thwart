package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.chooserName
import com.hasyame.marvelchampions.ui.util.labelRes

/**
 * The language the app is being displayed in.
 *
 * What a campaign template says follows this, not the card language: those are
 * two settings on purpose, and a French card list is a normal thing to read
 * with an English app. Card names are unaffected — they come from the card
 * database, in the card language, wherever they are shown.
 *
 * Read from the configuration rather than from settings so it is whatever the
 * app is really showing, including "follow the phone".
 */
private val campaignTextLocale: String
    @Composable get() = LocalConfiguration.current.locales[0].language

/** Maximum players a campaign supports. */
private const val MAX_PLAYERS = 4

/**
 * Page 0. Which campaign, what to call this run, who is playing, and how hard.
 *
 * A deck that is not legal cannot join: a campaign lasts five scenarios and
 * fixes its roster at the start, so an illegal deck would be a problem you
 * discover far too late. Cards missing from the collection are not a
 * legality problem and do not block anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartCampaignScreen(
    onBack: () -> Unit,
    onStarted: (String) -> Unit,
    viewModel: StartCampaignViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var chosenTemplateId by remember { mutableStateOf<String?>(null) }
    // Answers to whatever the chosen campaign asks. Reset with the campaign,
    // since another one's questions are not these.
    var choices by remember { mutableStateOf(mapOf<String, String>()) }
    var name by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    // Only asked for, and only required, when the campaign is played on expert.
    var standardSet by remember { mutableStateOf<String?>(null) }
    var roster by remember { mutableStateOf(emptyList<String>()) }

    val template: CampaignTemplate? =
        state.templates.firstOrNull { it.id == chosenTemplateId } ?: state.templates.firstOrNull()
    val difficulties = template?.difficulties.orEmpty()
    val effectiveDifficulty = difficulty.ifBlank { difficulties.firstOrNull().orEmpty() }
    // An expert campaign with no Standard set chosen describes a deck nobody
    // can build, so it cannot start any more than a campaign with no heroes.
    val needsStandardSet = effectiveDifficulty == EXPERT && state.standardSets.isNotEmpty()
    val canStart = template != null &&
        roster.isNotEmpty() &&
        effectiveDifficulty.isNotBlank() &&
        (!needsStandardSet || standardSet != null)

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.campaign_start)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.campaign_which)) {
                if (state.templates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.campaign_none_bundled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.templates.forEach { candidate ->
                        FilterChip(
                            selected = template?.id == candidate.id,
                            onClick = {
                                chosenTemplateId = candidate.id
                                choices = emptyMap()
                            },
                            label = { Text(candidate.chooserName(campaignTextLocale)) },
                        )
                    }
                }
            }

            // Only a campaign that has something to say shows this.
            template?.notice?.resolve(campaignTextLocale)?.takeIf { it.isNotBlank() }?.let { notice ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Shown on every campaign, complete or not: "100% and 100%" is the
            // answer to "is this one finished in my language?", and a line that
            // only appears when something is wrong cannot answer it.
            template?.id?.let { state.coverage[it] }?.let { coverage ->
                Text(
                    text = stringResource(
                        R.string.campaign_translation_coverage,
                        coverage.frenchPercent,
                        coverage.englishPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(stringResource(R.string.campaign_name)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(template?.name?.resolve(campaignTextLocale).orEmpty()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.campaign_roster)) {
                if (state.candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.campaign_no_decks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.candidates.forEach { candidate ->
                    val selected = candidate.deck.id in roster
                    Column {
                        FilterChip(
                            selected = selected,
                            // Four players is the ceiling, and an illegal deck
                            // never joins at all.
                            enabled = candidate.isLegal &&
                                (selected || roster.size < MAX_PLAYERS),
                            onClick = {
                                roster = if (selected) {
                                    roster - candidate.deck.id
                                } else {
                                    roster + candidate.deck.id
                                }
                            },
                            label = {
                                Text("${candidate.deck.name} — ${candidate.deck.heroName}")
                            },
                        )
                        if (!candidate.isLegal) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.campaign_deck_illegal,
                                    candidate.problems.size,
                                    candidate.problems.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.campaign_player_count,
                        roster.size,
                        roster.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // What the campaign itself asks, if anything. Fixed for the run,
            // like the roster and the difficulty beside it.
            template?.setupChoices?.forEach { choice ->
                Section(choice.label.resolve(campaignTextLocale)) {
                    val selected = choices[choice.id] ?: choice.options.firstOrNull()?.id
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choice.options.forEach { option ->
                            FilterChip(
                                selected = selected == option.id,
                                onClick = { choices = choices + (choice.id to option.id) },
                                label = { Text(option.label.resolve(campaignTextLocale)) },
                            )
                        }
                    }
                    choice.options.firstOrNull { it.id == selected }
                        ?.detail?.resolve(campaignTextLocale)
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }

            Section(stringResource(R.string.campaign_difficulty)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    difficulties.forEach { value ->
                        FilterChip(
                            selected = effectiveDifficulty == value,
                            onClick = { difficulty = value },
                            label = { Text(value.replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.campaign_roster_locked),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // An expert campaign shuffles the Expert set in with a Standard
            // set, so which Standard is a second question, asked once because
            // it is fixed for the whole run like everything else here.
            if (needsStandardSet) {
                Section(stringResource(R.string.session_standard_set)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.standardSets.forEach { set ->
                            val stored = set.name.lowercase()
                            FilterChip(
                                selected = standardSet == stored,
                                onClick = { standardSet = stored },
                                label = { Text(stringResource(set.labelRes())) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.session_standard_set_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (standardSet == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Button(
                onClick = {
                    template?.let {
                        viewModel.start(
                            it,
                            effectiveDifficulty,
                            standardSet.orEmpty(),
                            roster,
                            name,
                            choices,
                            onStarted,
                        )
                    }
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.campaign_lets_go)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/** The campaign templates' own word for the harder mode. */
private const val EXPERT = "expert"

/** A blank line between the campaign's own notice and the app's. */
private const val NOTICE_GAP = "\n\n"
