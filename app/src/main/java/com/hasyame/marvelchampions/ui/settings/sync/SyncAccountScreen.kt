package com.hasyame.marvelchampions.ui.settings.sync

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.sync.AdoptionCounts

/** Which form the signed-out half is showing. */
private enum class AccountForm { SIGN_IN, CREATE, RECOVER }

/**
 * The account, and the switch that decides whether this device syncs.
 *
 * The two are deliberately separate. Signing in records a token and stops;
 * nothing leaves the phone until the switch is turned on, and turning it on is
 * where the merge question is asked. Somebody who only wanted to see whether
 * their account still exists should not have their play history merged as a
 * side effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncAccountScreen(
    onBack: () -> Unit,
    viewModel: SyncAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.sync_title)) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            state.message?.let { message ->
                Text(
                    text = when (message) {
                        is SyncMessage.Resource ->
                            stringResource(message.text, *message.args.toTypedArray())

                        is SyncMessage.Plural ->
                            pluralStringResource(message.id, message.count, message.count)

                        is SyncMessage.FromServer -> message.text
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (state.signedIn) {
                SignedIn(state, viewModel)
            } else {
                SignedOut(state, viewModel)
            }
        }
    }

    state.recoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            onSave = viewModel::saveRecoveryCode,
            onAcknowledge = viewModel::acknowledgeRecoveryCode,
        )
    }

    state.adoption?.let { counts ->
        AdoptionDialog(
            counts = counts,
            onMerge = viewModel::merge,
            onKeepServerOnly = viewModel::keepServerOnly,
            onCancel = viewModel::cancelAdoption,
        )
    }
}

// --- signed out --------------------------------------------------------------

@Composable
private fun SignedOut(state: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    var form by remember { mutableStateOf(AccountForm.SIGN_IN) }
    var identifier by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    InstanceSection(state, viewModel)
    HorizontalDivider()

    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = form == AccountForm.SIGN_IN,
            onClick = { form = AccountForm.SIGN_IN },
            label = { Text(stringResource(R.string.sync_sign_in)) },
        )
        if (state.registrationOpen) {
            FilterChip(
                selected = form == AccountForm.CREATE,
                onClick = { form = AccountForm.CREATE },
                label = { Text(stringResource(R.string.sync_create)) },
            )
        }
        FilterChip(
            selected = form == AccountForm.RECOVER,
            onClick = { form = AccountForm.RECOVER },
            label = { Text(stringResource(R.string.sync_recover_entry)) },
        )
    }

    if (!state.registrationOpen && state.instanceReachable == true) {
        Text(
            text = stringResource(R.string.sync_registration_closed),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    // One box for the address. Signing in and recovering send whatever was
    // typed, which the server resolves as an address or as a pseudonym on an
    // account made before addresses existed.
    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        label = {
            Text(
                stringResource(
                    if (form == AccountForm.CREATE) {
                        R.string.sync_email
                    } else {
                        R.string.sync_identifier
                    },
                ),
            )
        },
        singleLine = true,
        supportingText = {
            Text(
                stringResource(
                    if (form == AccountForm.CREATE) {
                        R.string.sync_email_hint
                    } else {
                        R.string.sync_identifier_hint
                    },
                ),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    if (form == AccountForm.CREATE) {
        // The pseudonym, which is what you are called rather than how you get
        // back in. Only asked for when the account is being made.
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            label = { Text(stringResource(R.string.sync_handle)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    if (form == AccountForm.RECOVER) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.sync_recovery_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = {
            Text(
                stringResource(
                    if (form == AccountForm.RECOVER) {
                        R.string.sync_new_password
                    } else {
                        R.string.sync_password
                    },
                ),
            )
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Text(
        text = stringResource(R.string.sync_password_rule),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    Button(
        onClick = {
            when (form) {
                AccountForm.SIGN_IN -> viewModel.signIn(identifier, password)
                AccountForm.CREATE -> viewModel.register(handle, identifier, password)
                AccountForm.RECOVER -> viewModel.recover(identifier, code, password)
            }
        },
        // Filled in, and nothing more. The password rule is the server's, and
        // checking it here as well would be a second policy that goes stale the
        // day the first one changes.
        enabled = !state.busy &&
            identifier.isNotBlank() &&
            password.isNotBlank() &&
            (form != AccountForm.CREATE || handle.isNotBlank()) &&
            (form != AccountForm.RECOVER || code.isNotBlank()),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(
            stringResource(
                when (form) {
                    AccountForm.SIGN_IN -> R.string.sync_sign_in
                    AccountForm.CREATE -> R.string.sync_create
                    AccountForm.RECOVER -> R.string.sync_recover_entry
                },
            ),
        )
    }
}

@Composable
private fun InstanceSection(state: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    var url by remember(state.instanceUrl) { mutableStateOf(state.instanceUrl) }

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(R.string.sync_instance)) },
        singleLine = true,
        supportingText = {
            Text(
                when (state.instanceReachable) {
                    true -> stringResource(R.string.sync_instance_ok)
                    false -> stringResource(R.string.sync_instance_unreachable)
                    null -> stringResource(R.string.sync_instance_hint)
                },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (url.trim() != state.instanceUrl) {
        TextButton(
            onClick = { viewModel.setInstanceUrl(url) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.sync_instance_save)) }
    }
}

// --- signed in ---------------------------------------------------------------

@Composable
private fun SignedIn(state: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    var confirmingDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_signed_in_as, state.handle)) },
        supportingContent = {
            Column {
                // Absent on an account made before addresses existed, and then
                // simply not shown rather than shown as a gap.
                if (state.email.isNotBlank()) {
                    Text(state.email)
                }
                Text(state.instanceUrl)
            }
        },
    )
    HorizontalDivider()

    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_switch)) },
        supportingContent = { Text(stringResource(R.string.sync_switch_summary)) },
        trailingContent = {
            Switch(
                checked = state.enabled,
                enabled = !state.busy,
                onCheckedChange = viewModel::setEnabled,
            )
        },
    )

    if (!state.enabled) {
        Text(
            text = stringResource(R.string.sync_idle_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    } else {
        Text(
            text = if (state.pending == 0) {
                stringResource(R.string.sync_up_to_date)
            } else {
                stringResource(R.string.sync_pending, state.pending)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // When it last ran, in plain words. A screen that says nothing about
        // whether it is working is worse than one that admits it is not.
        Text(
            text = if (state.lastSyncedAt == 0L) {
                stringResource(R.string.sync_never)
            } else {
                stringResource(
                    R.string.sync_last,
                    DateUtils.getRelativeTimeSpanString(state.lastSyncedAt).toString(),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(
            onClick = viewModel::syncNow,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                stringResource(
                    if (state.busy) R.string.sync_working else R.string.sync_now,
                ),
            )
        }
    }
    HorizontalDivider()

    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_devices)) },
        supportingContent = {
            Column {
                state.devices.forEach { device ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (device.current) {
                                stringResource(R.string.sync_device_this)
                            } else {
                                device.name
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!device.current) {
                            TextButton(onClick = { viewModel.revokeDevice(device.id) }) {
                                Text(stringResource(R.string.sync_device_revoke))
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::loadDevices) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
        },
    )
    HorizontalDivider()

    ChangePassword(state, viewModel)
    HorizontalDivider()

    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_sign_out)) },
        supportingContent = { Text(stringResource(R.string.sync_sign_out_summary)) },
        trailingContent = {
            OutlinedButton(onClick = viewModel::signOut) {
                Text(stringResource(R.string.sync_sign_out))
            }
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_delete_account)) },
        supportingContent = { Text(stringResource(R.string.sync_delete_account_summary)) },
        trailingContent = {
            OutlinedButton(onClick = { confirmingDelete = true }) {
                Text(stringResource(R.string.sync_delete_account))
            }
        },
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.sync_delete_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sync_delete_account_confirm))
                    // The server asks for it again, and is right to: a device
                    // token can be stolen, and this is the one call that cannot
                    // be undone.
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text(stringResource(R.string.sync_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.isNotBlank(),
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteAccount(deletePassword)
                        deletePassword = ""
                    },
                ) { Text(stringResource(R.string.sync_delete_account)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        deletePassword = ""
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ChangePassword(state: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }

    OutlinedTextField(
        value = current,
        onValueChange = { current = it },
        label = { Text(stringResource(R.string.sync_current_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    OutlinedTextField(
        value = replacement,
        onValueChange = { replacement = it },
        label = { Text(stringResource(R.string.sync_new_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    TextButton(
        onClick = {
            viewModel.changePassword(current, replacement)
            current = ""
            replacement = ""
        },
        // Filled in, and nothing more: the rule the new one has to meet is
        // the server's, and it says so if it refuses.
        enabled = !state.busy && current.isNotBlank() && replacement.isNotBlank(),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { Text(stringResource(R.string.sync_change_password)) }
}

// --- dialogues ---------------------------------------------------------------

/**
 * The recovery code, shown once.
 *
 * There is no dismiss-by-tapping-outside and no cancel: the server keeps only a
 * fingerprint of this, and an account with no email address has nothing else to
 * fall back on. Somebody who closes this by accident has lost their way back in.
 */
@Composable
private fun RecoveryCodeDialog(
    code: String,
    onSave: (android.net.Uri) -> Unit,
    onAcknowledge: () -> Unit,
) {
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(onSave) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.sync_recovery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(code, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.sync_recovery_body))
                OutlinedButton(onClick = { saveFile.launch("thwart-recovery-code.txt") }) {
                    Text(stringResource(R.string.sync_recovery_save))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(stringResource(R.string.sync_recovery_saved))
            }
        },
    )
}

/**
 * The question that has to be asked before a merge, with the counts in the
 * user's own terms.
 *
 * Not politeness. It is the difference between a feature people trust and one
 * that eats a stranger's campaign log the first time they try it, and the whole
 * of the adoption design exists so that this dialogue can be shown while
 * nothing at all has been written.
 */
/**
 * "26 plays, 2 decks and 4 campaigns", inflected.
 *
 * Three counts in one sentence, so it cannot be one plural resource: a plural
 * takes one quantity. Each is inflected on its own and then joined, which is
 * also what lets the French say "1 partie" and "2 parties" without the English
 * sentence having to change shape.
 */
@Composable
private fun counted(of: (SyncCollection) -> Int): String {
    val plays = of(SyncCollection.PLAYS)
    val decks = of(SyncCollection.SAVED_DECKS)
    val campaigns = of(SyncCollection.CAMPAIGN_RUNS)
    return listOf(
        pluralStringResource(R.plurals.sync_count_plays, plays, plays),
        pluralStringResource(R.plurals.sync_count_decks, decks, decks),
        pluralStringResource(R.plurals.sync_count_campaigns, campaigns, campaigns),
    ).joinToString(", ")
}

@Composable
private fun AdoptionDialog(
    counts: AdoptionCounts,
    onMerge: () -> Unit,
    onKeepServerOnly: (android.net.Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val exportThenReplace = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(onKeepServerOnly) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_adopt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_adopt_server, counted { counts.server(it) }))
                Text(
                    if (counts.nothingToContribute) {
                        stringResource(R.string.sync_adopt_local_none)
                    } else {
                        stringResource(R.string.sync_adopt_local, counted { counts.localOnly(it) })
                    },
                )
                // The third answer sits in the body rather than beside the
                // other two. A dialogue's button row is one line: stacking
                // three there clipped the last of them off the bottom, and the
                // one that vanished was Cancel.
                OutlinedButton(
                    onClick = { exportThenReplace.launch("thwart-before-merge.zip") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_adopt_server_only)) }
                Text(
                    text = stringResource(R.string.sync_adopt_server_only_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge) { Text(stringResource(R.string.sync_adopt_merge)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
