package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R

/**
 * Puts the clock right.
 *
 * The stop button gets forgotten, and a game recorded as four hours long
 * because the phone sat in a bag afterwards is worse than one with no time at
 * all: it lands in the statistics and quietly drags every average with it.
 *
 * Shared by the one-off game and the campaign rather than written twice. It was
 * only on the one-off screen for a long time, which meant a campaign scenario
 * was the one game whose time could not be corrected, for no reason anybody had
 * decided on.
 *
 * @param onConfirm receives the new elapsed time in milliseconds.
 */
@Composable
fun CorrectTimeDialog(
    elapsedMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    // Minutes rather than the full clock: nobody knows the seconds, and asking
    // for them invites a wrong answer typed precisely.
    var minutes by remember { mutableStateOf((elapsedMillis / 60_000L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_correct_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.session_correct_message))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.session_correct_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm((minutes.toLongOrNull() ?: 0L) * 60_000L) },
            ) { Text(stringResource(R.string.session_correct_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
