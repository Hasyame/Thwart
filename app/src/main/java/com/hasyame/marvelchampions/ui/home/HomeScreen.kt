package com.hasyame.marvelchampions.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.ui.achievements.AchievementBadgeImage
import com.hasyame.marvelchampions.ui.achievements.AchievementCard
import com.hasyame.marvelchampions.ui.achievements.descriptionOf
import com.hasyame.marvelchampions.ui.achievements.titleOf
import com.hasyame.marvelchampions.ui.navigation.NavigationIcons

/**
 * The home page: what this version changed, a menu of the things done
 * most, and where Thwart lives outside the phone. Nothing on it is fetched;
 * the notes come from the build and the links open the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    /** Opens the account page; true asks for the "create an account" form. */
    onAccount: (create: Boolean) -> Unit,
    onHistory: () -> Unit,
    onAchievements: () -> Unit,
    onStats: () -> Unit,
    onRules: () -> Unit,
    onCollection: () -> Unit,
    onRandomGame: () -> Unit,
    onCard: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strip by viewModel.achievementStrip.collectAsStateWithLifecycle()
    var notesOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val browser = LocalUriHandler.current

    LaunchedEffect(state.randomCardCode) {
        state.randomCardCode?.let { code ->
            viewModel.consumeRandomCard()
            onCard(code)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.destination_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .halftone(MaterialTheme.colorScheme.onBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The menu, two by two: the rules and the collection, a game and
            // a card at random, then the games played and what they add up to.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(stringResource(R.string.destination_rules), NavigationIcons.Book, onRules, Modifier.weight(1f))
                MenuTile(stringResource(R.string.collection_title), NavigationIcons.Card, onCollection, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(stringResource(R.string.play_random), Icons.Filled.Refresh, onRandomGame, Modifier.weight(1f))
                MenuTile(stringResource(R.string.home_random_card), NavigationIcons.Deck, viewModel::drawRandomCard, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(stringResource(R.string.history_title), Icons.Filled.DateRange, onHistory, Modifier.weight(1f))
                MenuTile(stringResource(R.string.destination_stats), NavigationIcons.Chart, onStats, Modifier.weight(1f))
            }


            if (!state.notesDismissed && state.notes != null) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { browser.openUri(RELEASES_URL) }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.home_all_releases))
                        }
                        Text(
                            stringResource(R.string.home_whats_new),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::dismissNotes) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_dismiss_notes))
                        }
                    }
                    Text(
                        state.versionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { notesOpen = !notesOpen }) {
                        Text(stringResource(if (notesOpen) R.string.home_hide_details else R.string.home_show_details))
                    }
                    if (notesOpen) Text(
                        state.notes.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // The account, between the news and the menu: what signing in is
            // for while nobody is, and a greeting by name once somebody is.
            // A tap on either opens the account page, where the sync switch
            // lives.
            AccountPanel(handle = state.accountHandle, onAccount = onAccount)

            // The achievements, straight under the name, the way a store's
            // front page shows them: the count, the latest earned, the badges.
            strip?.let { AchievementsPanel(it, onAchievements) }


            Panel {
                Text(
                    stringResource(R.string.home_links_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.home_links_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LinkRow(
                    title = stringResource(R.string.settings_patreon),
                    subtitle = stringResource(R.string.home_patreon_note),
                    icon = { Icon(painterResource(R.drawable.ic_patreon), contentDescription = null, tint = Color.Unspecified) },
                    onClick = { browser.openUri(PATREON_URL) },
                )
                LinkRow(
                    title = stringResource(R.string.home_web),
                    subtitle = stringResource(R.string.home_web_note),
                    icon = { Icon(NavigationIcons.Deck, contentDescription = null) },
                    onClick = { browser.openUri(WEB_URL) },
                )
                LinkRow(
                    title = stringResource(R.string.home_github),
                    subtitle = stringResource(R.string.home_github_note),
                    icon = { Icon(NavigationIcons.Book, contentDescription = null) },
                    onClick = { browser.openUri(GITHUB_URL) },
                )
            }
        }
    }
}

/**
 * Signed out: what an account does, and the two ways to get one. Signed
 * in: the pseudonym, as a greeting, since the rest is on the account page.
 */
@Composable
private fun AccountPanel(handle: String?, onAccount: (create: Boolean) -> Unit) {
    if (handle != null) {
        Surface(
            onClick = { onAccount(false) },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_ready_to_play, handle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.sync_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    Panel {
        Text(
            stringResource(R.string.home_account_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.home_account_note),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Button(onClick = { onAccount(false) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_sign_in))
        }
        TextButton(onClick = { onAccount(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sync_create))
        }
    }
}

/**
 * The achievements as a store's front page shows them: how many, the
 * latest one with its words, the earned badges in a row, then the ones
 * still to earn, greyed. Every part opens the achievements page.
 */
@Composable
private fun AchievementsPanel(strip: HomeAchievements, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val percent = if (strip.total == 0) 0 else Math.round(strip.unlocked * 100f / strip.total)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.achievements_home_count, strip.unlocked, strip.unlocked, strip.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "(" + stringResource(R.string.achievements_completion_rate, percent) + ")",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { if (strip.total == 0) 0f else strip.unlocked.toFloat() / strip.total },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            )
            strip.latest?.let { latest ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    AchievementBadgeImage(latest.badge, unlocked = true, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(titleOf(latest), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            descriptionOf(latest),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (strip.unlockedCards.isNotEmpty()) {
                BadgeRow(strip.unlockedCards, unlocked = true)
            }
            if (strip.lockedCards.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    stringResource(R.string.achievements_home_locked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                BadgeRow(strip.lockedCards, unlocked = false)
            }
            Text(
                stringResource(R.string.achievements_home_see),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** As many badges as fit a row, and "+N" for the rest. */
@Composable
private fun BadgeRow(cards: List<AchievementCard>, unlocked: Boolean) {
    val shown = cards.take(BADGES_IN_A_ROW)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { card -> AchievementBadgeImage(card.badge, unlocked = unlocked, size = 40.dp) }
        if (cards.size > shown.size) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.achievements_home_more, cards.size - shown.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val BADGES_IN_A_ROW = 6

/** A rounded panel, like the ones the reference app stacks on its home page. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** One of the menu's buttons: an icon over a word, big enough to be a decision. */
@Composable
private fun MenuTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.height(96.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val PATREON_URL = "https://www.patreon.com/cw/thwart"
private const val WEB_URL = "https://thwart.app"
private const val GITHUB_URL = "https://github.com/Hasyame/Thwart"
private const val RELEASES_URL = "https://github.com/Hasyame/Thwart/releases"
