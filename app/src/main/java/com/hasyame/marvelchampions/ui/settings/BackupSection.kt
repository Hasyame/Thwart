package com.hasyame.marvelchampions.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.backup.BackupSummary
import java.text.DateFormat
import java.util.Date

/**
 * Saving a copy of everything, and putting one back.
 *
 * The app has no account and no server, so a lost phone takes the collection,
 * every deck, every campaign and the whole play history with it. This is the
 * only thing standing between the player and that.
 */
@Composable
fun BackupSection(
    pendingRestore: BackupSummary?,
    message: String?,
    suggestedFileName: (Boolean) -> String,
    onExport: (android.net.Uri, Boolean) -> Unit,
    onFileChosen: (android.net.Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    // Off by default. A backup gets handed to people and dropped in shared
    // drives, and a photograph of somebody's living room should not travel
    // unless they said so.
    var withPhotos by remember { mutableStateOf(false) }

    // The system picker, so the file lands wherever the player keeps things:
    // Drive, the SD card, anywhere. The app never needs storage permission.
    // Two of them, because the type is fixed when the launcher is made and
    // an archive is not a JSON document.
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { onExport(it, false) } }

    val createArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { onExport(it, true) } }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFileChosen) }

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_backup),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_backup_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { withPhotos = !withPhotos },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = withPhotos, onCheckedChange = { withPhotos = it })
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.settings_backup_photos),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_backup_photos_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val name = suggestedFileName(withPhotos)
                    if (withPhotos) createArchive.launch(name) else createDocument.launch(name)
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_export)) }

            OutlinedButton(
                // Anything, not just application/json: a file that has been
                // round-tripped through a cloud drive often comes back typed as
                // octet-stream, and refusing it would be baffling.
                onClick = { openFile.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_restore)) }
        }
    }

    pendingRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.settings_restore_contents,
                            DateFormat.getDateInstance().format(Date(summary.createdAt)),
                            summary.decks,
                            summary.campaigns,
                            summary.plays,
                            summary.ownedPacks,
                        ),
                    )
                    // Only when the file carries them, so a plain backup
                    // does not read as though it were missing something.
                    if (summary.photos > 0) {
                        Text(
                            stringResource(
                                R.string.settings_restore_photos,
                                summary.photos,
                            ),
                        )
                    }
                    // Said plainly, because it is the part that cannot be undone.
                    Text(
                        text = stringResource(R.string.settings_restore_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text(stringResource(R.string.settings_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = onDismissMessage,
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = onDismissMessage) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
}
