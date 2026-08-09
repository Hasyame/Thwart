package com.hasyame.marvelchampions.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.ModularSet
import com.hasyame.marvelchampions.data.repository.PackOwnership
import com.hasyame.marvelchampions.domain.model.PackType

/**
 * F1. A full screen of its own rather than a section inside settings, because
 * it is the source of truth for the randomiser and for deck legality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Held here rather than in the row: LazyColumn recycles its children, and
    // an expansion kept inside one would collapse itself on scroll.
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.collection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = pluralStringResource(
                        R.plurals.collection_owned_count,
                        state.totalCount,
                        state.ownedCount,
                        state.totalCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                BulkActions(
                    onSelectType = { type -> viewModel.selectAllOfType(type, owned = true) },
                    onSelectAll = { viewModel.selectAll(owned = true) },
                    onClearAll = { viewModel.selectAll(owned = false) },
                )
                HorizontalDivider()
            }

            state.waves.forEach { group ->
                item(key = "wave-${group.wave}") {
                    Text(
                        text = stringResource(R.string.collection_wave, group.wave),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(group.packs, key = { it.pack.code }) { ownership ->
                    val sets = state.modularSetsByPack[ownership.pack.code].orEmpty()
                    val scenarios = state.scenariosByPack[ownership.pack.code].orEmpty()
                    PackRow(
                        ownership = ownership,
                        // Only an owned pack can have things missing from it, and
                        // a pack with none has nothing to open.
                        modularSets = if (ownership.isOwned) sets else emptyList(),
                        scenarios = if (ownership.isOwned) scenarios else emptyList(),
                        expanded = ownership.pack.code in expanded,
                        excludedSets = state.excludedModularSets,
                        excludedScenarios = state.excludedScenarios,
                        onToggle = { viewModel.setOwned(ownership.pack.code, it) },
                        onToggleExpanded = {
                            expanded = if (ownership.pack.code in expanded) {
                                expanded - ownership.pack.code
                            } else {
                                expanded + ownership.pack.code
                            }
                        },
                        onToggleSet = { code, owned -> viewModel.setModularSetOwned(code, owned) },
                        onToggleScenario = { code, owned ->
                            viewModel.setScenarioOwned(code, owned)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActions(
    onSelectType: (PackType) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onSelectAll,
            label = { Text(stringResource(R.string.collection_select_all)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.CORE) },
            label = { Text(stringResource(R.string.collection_select_core)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.HERO_PACK) },
            label = { Text(stringResource(R.string.collection_select_hero_packs)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.SCENARIO_PACK) },
            label = { Text(stringResource(R.string.collection_select_scenario_packs)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.CAMPAIGN_BOX) },
            label = { Text(stringResource(R.string.collection_select_campaign_boxes)) },
        )
        AssistChip(
            onClick = onClearAll,
            label = { Text(stringResource(R.string.collection_clear_all)) },
        )
    }
}

@Composable
private fun PackRow(
    ownership: PackOwnership,
    modularSets: List<ModularSet>,
    scenarios: List<ModularSet>,
    expanded: Boolean,
    excludedSets: Set<String>,
    excludedScenarios: Set<String>,
    onToggle: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
    onToggleSet: (String, Boolean) -> Unit,
    onToggleScenario: (String, Boolean) -> Unit,
) {
    val contents = scenarios + modularSets
    val missing = scenarios.count { it.code in excludedScenarios } +
        modularSets.count { it.code in excludedSets }

    ListItem(
        modifier = if (contents.isEmpty()) {
            Modifier
        } else {
            Modifier.clickable(onClick = onToggleExpanded)
        },
        headlineContent = { Text(ownership.name) },
        supportingContent = {
            Text(
                text = buildString {
                    append(packTypeLabel(ownership.pack.type))
                    if (ownership.pack.known < ownership.pack.total) {
                        append(" · ")
                        append(
                            pluralStringResource(
                                R.plurals.collection_partial_pack,
                                ownership.pack.total,
                                ownership.pack.known,
                                ownership.pack.total,
                            ),
                        )
                    }
                    // Worth saying on the collapsed row: an exclusion made once
                    // and forgotten would otherwise quietly shrink every draw.
                    if (missing > 0) {
                        append(" · ")
                        append(
                            pluralStringResource(
                                R.plurals.collection_missing_sets,
                                missing,
                                missing,
                            ),
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contents.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.collection_hide_modular_sets
                            } else {
                                R.string.collection_show_modular_sets
                            },
                        ),
                    )
                }
                Switch(checked = ownership.isOwned, onCheckedChange = onToggle)
            }
        },
    )

    if (expanded && contents.isNotEmpty()) {
        Text(
            text = stringResource(R.string.collection_contents_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 4.dp),
        )
        // Scenarios first, then modular sets: a box is remembered by what you
        // play in it, not by what you shuffle into the encounter deck.
        ContentGroup(
            title = stringResource(R.string.collection_scenarios),
            entries = scenarios,
            excluded = excludedScenarios,
            onToggle = onToggleScenario,
        )
        ContentGroup(
            title = stringResource(R.string.collection_modular_sets),
            entries = modularSets,
            excluded = excludedSets,
            onToggle = onToggleSet,
        )
    }
}

/** One tickable list inside an expanded pack. */
@Composable
private fun ContentGroup(
    title: String,
    entries: List<ModularSet>,
    excluded: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    if (entries.isEmpty()) {
        return
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 4.dp),
    )
    entries.forEach { entry ->
        val owned = entry.code !in excluded
        ListItem(
            modifier = Modifier
                .padding(start = 32.dp)
                .clickable { onToggle(entry.code, !owned) },
            headlineContent = {
                Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
            },
            leadingContent = {
                Checkbox(checked = owned, onCheckedChange = { onToggle(entry.code, it) })
            },
        )
    }
}

@Composable
private fun packTypeLabel(type: String): String = when (PackType.fromName(type)) {
    PackType.CORE -> stringResource(R.string.pack_type_core)
    PackType.HERO_PACK -> stringResource(R.string.pack_type_hero)
    PackType.SCENARIO_PACK -> stringResource(R.string.pack_type_scenario)
    PackType.CAMPAIGN_BOX -> stringResource(R.string.pack_type_campaign_box)
    PackType.MODULAR_SET -> stringResource(R.string.pack_type_modular_set)
    PackType.UNKNOWN -> stringResource(R.string.pack_type_unknown)
}
