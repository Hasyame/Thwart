package com.hasyame.marvelchampions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.sync.CardSyncState

/**
 * The first thing a new install shows: fetching the cards.
 *
 * The card database is not in the package. It belongs to MarvelCDB, it changes
 * when MarvelCDB changes, and a snapshot signed into an APK would be both
 * somebody else's content and out of date the week after. So it is fetched
 * once, here, and everything afterwards works offline.
 *
 * About a megabyte and a half, and a few seconds. Said plainly on the screen,
 * because a download nobody warned you about is what makes people close an app.
 */
@Composable
fun FirstRunCardsScreen(
    state: CardSyncState,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
) {
    // The work outlives this screen, so success is noticed rather than
    // returned: the download survives a rotation, and finishing while the
    // screen is being rebuilt still moves the app on.
    LaunchedEffect(state) {
        if (state is CardSyncState.Succeeded) {
            onFinished()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.first_run_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.first_run_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        when (state) {
            is CardSyncState.Running -> {
                // Indeterminate on purpose: the worker reports which step it is
                // on, not how far through it is, and a bar that fakes progress
                // it does not have is worse than one that admits it.
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.first_run_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is CardSyncState.Failed -> {
                Text(
                    text = state.message?.let {
                        stringResource(R.string.first_run_failed_detail, it)
                    } ?: stringResource(R.string.first_run_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.first_run_retry))
                }
            }

            else -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_run_download))
            }
        }

        // Never a dead end. Somebody with no signal can still read the rules
        // reference, and being told to come back later when they opened the app
        // to settle an argument at the table is the worse answer.
        if (state !is CardSyncState.Running) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.first_run_skip))
            }
        }
    }
}
