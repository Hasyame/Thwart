package com.hasyame.marvelchampions.ui.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.DraftHero
import com.hasyame.marvelchampions.domain.draft.DraftCard
import com.hasyame.marvelchampions.domain.draft.DraftEngine
import com.hasyame.marvelchampions.domain.draft.DraftPhase
import com.hasyame.marvelchampions.domain.draft.DraftPlayer
import com.hasyame.marvelchampions.domain.draft.DraftRules
import com.hasyame.marvelchampions.domain.draft.DraftSettings
import com.hasyame.marvelchampions.domain.draft.IdentityMode
import com.hasyame.marvelchampions.ui.decks.problemMessage
import com.hasyame.marvelchampions.ui.util.aspectLabel

/** A printed card is 63 by 88 mm. */
private const val CARD_RATIO = 63f / 88f

/**
 * The draft, from the settings to the saved decks: one screen whose page is
 * the phase the draft is in, so that reopening the app lands on the same
 * page the player left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCardDetail: (String) -> Unit,
    viewModel: DraftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmAbandon by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedDeckIds) {
        if (state.savedDeckIds != null) {
            onSaved()
        }
    }

    val isWide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(stringResource(R.string.draft_abandon)) },
            text = { Text(stringResource(R.string.draft_abandon_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbandon = false
                    viewModel.abandon()
                }) { Text(stringResource(R.string.draft_abandon_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    state.message?.let { message -> MessageDialog(message, viewModel) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.draft_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.phase != DraftPhase.SETUP) {
                        TextButton(onClick = { confirmAbandon = true }) {
                            Text(stringResource(R.string.draft_abandon))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.phase == DraftPhase.SETUP -> SetupPage(state, viewModel)
                state.phase == DraftPhase.IDENTITY -> IdentityPage(state, viewModel, isWide)
                state.phase == DraftPhase.PICK -> PickPage(state, viewModel, isWide, onCardDetail)
                state.phase == DraftPhase.FINISH -> FinishPage(state, viewModel)
            }
        }
    }
}

@Composable
private fun MessageDialog(message: DraftMessage, viewModel: DraftViewModel) {
    when (message) {
        DraftMessage.NoHeroes -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.draft_title)) },
            text = { Text(stringResource(R.string.draft_no_heroes)) },
            confirmButton = {},
        )

        is DraftMessage.Shortfalls -> AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(stringResource(R.string.draft_shortfall_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.shortfalls.forEach { short ->
                        Text(stringResource(R.string.draft_shortfall_line, short.playerIndex + 1, short.needed, short.available))
                    }
                    Text(stringResource(R.string.draft_shortfall_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                val first = message.shortfalls.first().playerIndex
                TextButton(onClick = { viewModel.reviseIdentity(first) }) {
                    Text(stringResource(R.string.draft_shortfall_revise, first + 1))
                }
            },
        )

        is DraftMessage.Illegal -> AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(stringResource(R.string.draft_illegal_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.draft_illegal_detail, message.playerIndex + 1))
                    message.problems.forEach { Text(problemMessage(it), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

// --- page 1: settings ------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(state: DraftUiState, viewModel: DraftViewModel) {
    val settings = state.draft?.settings ?: DraftSettings()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(stringResource(R.string.draft_setup_players)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (DraftSettings.MIN_PLAYERS..DraftSettings.MAX_PLAYERS).forEach { count ->
                    FilterChip(
                        selected = settings.players == count,
                        onClick = { viewModel.setPlayers(count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
        }

        Section(stringResource(R.string.draft_setup_identity_mode)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IdentityMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.identityMode == mode,
                        onClick = { viewModel.setIdentityMode(mode) },
                        label = { Text(modeLabel(mode)) },
                    )
                }
            }
        }

        Section(stringResource(R.string.draft_setup_offer_size)) {
            StepSlider(
                value = settings.offerSize,
                range = DraftSettings.MIN_OFFER_SIZE..DraftSettings.MAX_OFFER_SIZE,
                onChange = viewModel::setOfferSize,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { viewModel.setSynergyOnly(!settings.synergyOnly) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = settings.synergyOnly, onCheckedChange = viewModel::setSynergyOnly)
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.draft_setup_synergy), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.draft_setup_synergy_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = viewModel::beginIdentities,
            enabled = state.heroes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.draft_setup_begin)) }
    }
}

@Composable
private fun modeLabel(mode: IdentityMode): String = when (mode) {
    IdentityMode.RANDOM -> stringResource(R.string.draft_mode_random)
    IdentityMode.RANDOM_OF_FIVE -> stringResource(R.string.draft_mode_random_of_five)
    IdentityMode.CHOICE -> stringResource(R.string.draft_mode_choice)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** A slider over whole numbers, with the number beside it. */
@Composable
private fun StepSlider(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

// --- page 2: identity ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityPage(state: DraftUiState, viewModel: DraftViewModel, isWide: Boolean) {
    val draft = state.draft ?: return
    val player = draft.currentPlayer
    val hero = state.hero(player.heroCode)
    val mode = draft.settings.identityMode
    val taken = draft.players.filter { it.index != player.index && it.heroCode != null }.associate { it.heroCode!! to it.index }

    // Which identities this page lists: the five drawn, or the whole shelf.
    val listed = when {
        mode == IdentityMode.RANDOM_OF_FIVE && player.heroChoices.isNotEmpty() ->
            player.heroChoices.mapNotNull { code -> state.hero(code) }
        mode == IdentityMode.CHOICE -> state.heroes
        else -> emptyList()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isWide) 160.dp else 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.draft_identity_title, player.index + 1),
                    style = MaterialTheme.typography.titleLarge,
                )
                when (mode) {
                    IdentityMode.RANDOM -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        hero?.let { HeroCard(it, selected = true, takenBy = null, onClick = null, modifier = Modifier.width(140.dp)) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.draft_identity_drawn), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = viewModel::drawAgain) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.draft_identity_draw_again))
                            }
                        }
                    }
                    IdentityMode.RANDOM_OF_FIVE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.draft_identity_choose_five), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = viewModel::drawAgain) { Text(stringResource(R.string.draft_identity_draw_again)) }
                    }
                    IdentityMode.CHOICE -> Text(stringResource(R.string.draft_identity_choose), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        items(listed, key = { it.card.code }) { choice ->
            val takenBy = taken[choice.card.code]
            HeroCard(
                hero = choice,
                selected = choice.card.code == player.heroCode,
                takenBy = takenBy,
                onClick = if (takenBy == null) ({ viewModel.chooseHero(choice.card.code) }) else null,
            )
        }

        if (hero != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AspectsAndSize(state, player, hero, viewModel)
            }
        }
    }
}

@Composable
private fun HeroCard(
    hero: DraftHero,
    selected: Boolean,
    takenBy: Int?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardArt(hero.card.imageSrc, dimmed = takenBy != null)
        Text(
            hero.card.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        takenBy?.let {
            Text(
                stringResource(R.string.draft_identity_taken, it + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A card's picture, or a blank the same shape while it loads or when there is none. */
@Composable
private fun CardArt(imageSrc: String?, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        MarvelCdbUrls.cardImage(imageSrc)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AspectsAndSize(state: DraftUiState, player: DraftPlayer, hero: DraftHero, viewModel: DraftViewModel) {
    val draft = state.draft ?: return
    val imposed = DraftEngine.imposedAspects(hero.rules)
    val choices = DraftEngine.aspectChoices(hero.rules, state.poolAspectAvailable)
    val isLast = draft.current + 1 >= draft.players.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.draft_aspects_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (imposed != null) {
            Text(stringResource(R.string.draft_aspects_imposed), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(pluralStringResource(R.plurals.draft_aspects_choose, hero.rules.aspectCount, hero.rules.aspectCount), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { aspect ->
                    val tint = aspectColor(aspect)
                    FilterChip(
                        selected = aspect in player.aspects,
                        onClick = { viewModel.toggleAspect(aspect) },
                        label = { Text(aspectLabel(aspect)) },
                        leadingIcon = tint?.let { { Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(it)) } },
                    )
                }
                OutlinedButton(onClick = viewModel::drawAspects) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.draft_aspects_draw))
                }
            }
        }

        Text(stringResource(R.string.draft_deck_size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        StepSlider(
            value = player.deckSize,
            range = DraftRules.MIN_DECK_SIZE..DraftRules.MAX_DECK_SIZE,
            onChange = viewModel::setDeckSize,
        )
        Text(
            stringResource(R.string.draft_deck_size_detail, player.deckSize, player.signatureCount, player.deckSize - player.signatureCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = viewModel::confirmIdentity,
            enabled = player.isReady && (imposed != null || player.aspects.size == hero.rules.aspectCount),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (isLast) R.string.draft_identity_start else R.string.draft_identity_confirm))
        }
    }
}

// --- page 3: the table --------------------------------------------------------------

/**
 * The table: who is drafting and how far along, the cards on offer, and the
 * deck so far. A tap on a card takes it; a long press opens the card's own
 * page. A phone scrolls the lot as one column; a tablet keeps the deck beside
 * the offer.
 */
@Composable
private fun PickPage(state: DraftUiState, viewModel: DraftViewModel, isWide: Boolean, onCardDetail: (String) -> Unit) {
    val player = state.currentPlayer ?: return
    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            OfferGrid(state, player, viewModel, onCardDetail, columns = GridCells.Adaptive(170.dp), modifier = Modifier.weight(3f).fillMaxSize())
            DeckPanel(state, player, Modifier.weight(2f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))
        }
    } else {
        OfferGrid(state, player, viewModel, onCardDetail, columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
            DeckPanel(state, player, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun OfferGrid(
    state: DraftUiState,
    player: DraftPlayer,
    viewModel: DraftViewModel,
    onCardDetail: (String) -> Unit,
    columns: GridCells,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { TableHeader(player) }
        if (state.offer.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyOffer(player, viewModel) }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.draft_pick_round, player.picks.size + 1),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.offer, key = { it.canonicalCode }) { card ->
                OfferedCard(
                    card = card,
                    onTake = { viewModel.pick(card.canonicalCode) },
                    onDetail = { onCardDetail(card.canonicalCode) },
                )
            }
        }
        footer?.let { item(span = { GridItemSpan(maxLineSpan) }) { it() } }
    }
}

@Composable
private fun TableHeader(player: DraftPlayer) {
    val picksTotal = player.deckSize - player.signatureCount
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(player.heroName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    player.aspects.map { aspectLabel(it) }.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = aspectColor(player.aspects.firstOrNull()) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.draft_pick_progress, player.picks.size, picksTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.draft_picks_made),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (picksTotal == 0) 1f else player.picks.size.toFloat() / picksTotal },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The shelf has nothing legal left for this player. */
@Composable
private fun EmptyOffer(player: DraftPlayer, viewModel: DraftViewModel) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(pluralStringResource(R.plurals.draft_pick_empty, player.cardCount, player.cardCount), textAlign = TextAlign.Center)
        Button(onClick = viewModel::skipCurrent) { Text(stringResource(R.string.draft_pick_skip)) }
    }
}

/** A card on the table: the picture, its name, its type and cost. Tap to take, hold to read. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfferedCard(card: DraftCard, onTake: () -> Unit, onDetail: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onTake, onLongClick = onDetail)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardArt(card.imageSrc)
        Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        Text(
            listOfNotNull(card.typeName, card.cost?.let { stringResource(R.string.card_stat_cost) + " " + it }).joinToString(" \u00b7 "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The deck so far: the identity's own cards and every pick, by type, with costs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckPanel(state: DraftUiState, player: DraftPlayer, modifier: Modifier = Modifier) {
    val groups = remember(state.deck) { state.deck.groupBy { it.card.typeName } }
    ComicPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.draft_your_deck),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.draft_pick_progress, player.cardCount, player.deckSize),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.draft_deck_identity),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(player.heroName)
                player.aspects.forEach { Pill(aspectLabel(it), aspectColor(it)) }
            }
            groups.forEach { (type, lines) ->
                Text(
                    "$type (${lines.sumOf { it.count }})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                lines.forEach { line -> DeckRow(line) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, tint: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = tint ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DeckRow(line: DeckLine) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${line.count}\u00d7",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(aspectColor(line.card.factionCode) ?: MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(8.dp))
        Text(line.card.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        line.card.cost?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- page 4: the end ----------------------------------------------------------------

@Composable
private fun FinishPage(state: DraftUiState, viewModel: DraftViewModel) {
    val draft = state.draft ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.draft_finish_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.draft_finish_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        draft.players.forEach { player ->
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.draft_player_n, player.index + 1) + " · " + player.heroName +
                            " · " + player.aspects.map { aspectLabel(it) }.joinToString(", "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(pluralStringResource(R.plurals.decks_card_total, player.cardCount, player.cardCount), style = MaterialTheme.typography.bodySmall)
                    if (player.cardCount < DraftRules.MIN_DECK_SIZE) {
                        Text(
                            pluralStringResource(R.plurals.draft_finish_short, player.cardCount, player.cardCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = player.deckName.orEmpty(),
                        onValueChange = { viewModel.setDeckName(player.index, it) },
                        label = { Text(stringResource(R.string.draft_finish_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Button(
            onClick = viewModel::finish,
            enabled = !state.isSaving && draft.players.all { it.cardCount >= DraftRules.MIN_DECK_SIZE && !it.deckName.isNullOrBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
            } else {
                Text(stringResource(R.string.draft_finish_save))
            }
        }
    }
}
