package com.hasyame.marvelchampions.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import java.text.Normalizer

/** One option a picker can be set to: what is stored, and what is read. */
data class ChoiceOption(
    val id: String,
    val label: String,
    /**
     * Where it came from — the pack, for a scenario or a modular set.
     *
     * Without it a scenario is unfindable by the thing a player remembers. The
     * Green Goblin pack contains "Entreprise à Risques" and "Formule Mutagène",
     * and somebody looking for the Goblin scenarios reasonably concluded they
     * were missing, because neither name mentions him.
     */
    val detail: String? = null,
)

/**
 * Picks one or several values from a list that may be long.
 *
 * Used by the randomiser, where choosing a value locks that row of the draw,
 * and by the custom game setup, where it is simply how you pick. Both face the
 * same problem once a collection is complete: sixty scenarios and eighty
 * modular sets do not fit in a row of chips, and scrolling a wall of them to
 * find one you already know the name of is the worst way to ask.
 *
 * [limit] is how many may be picked: one for a scenario or a difficulty,
 * several for heroes and modular sets. When it is one the list behaves as a
 * radio group and closes on the tap, because a confirm button for a single
 * choice is a tap nobody needs.
 */
@Composable
fun ChooseValueDialog(
    title: String,
    options: List<ChoiceOption>,
    selected: List<String>,
    limit: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    // Kept in pick order: for heroes and aspects that order is the seating,
    // so the first one chosen is the first player.
    //
    // Deliberately unkeyed. Keying on `options` looked tidier but the caller
    // builds that list inline, so every recomposition handed over a new list
    // instance, re-ran the remember, and threw away what had just been ticked —
    // the dialog looked responsive and did nothing.
    val picked = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var tooMany by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val searchable = options.size > SEARCH_THRESHOLD
    val shown = remember(options, query) {
        if (query.isBlank()) options else options.filter { it.matches(query) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (limit > 1) {
                    Text(
                        text = stringResource(R.string.randomizer_choose_hint, limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tooMany) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (searchable) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.cards_search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.choose_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(shown, key = { it.id }) { option ->
                        val isPicked = option.id in picked
                        ListItem(
                            headlineContent = { Text(option.label) },
                            supportingContent = option.detail?.let {
                                { Text(it, style = MaterialTheme.typography.bodySmall) }
                            },
                            leadingContent = {
                                if (limit == 1) {
                                    RadioButton(selected = isPicked, onClick = null)
                                } else {
                                    Checkbox(checked = isPicked, onCheckedChange = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable {
                                when {
                                    limit == 1 -> onConfirm(listOf(option.id))
                                    isPicked -> {
                                        picked.remove(option.id)
                                        tooMany = false
                                    }
                                    picked.size < limit -> {
                                        picked.add(option.id)
                                        tooMany = false
                                    }
                                    // Silently ignoring the tap reads as a
                                    // broken list, so the hint turns red.
                                    else -> tooMany = true
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (limit > 1) {
                TextButton(
                    onClick = { onConfirm(picked.toList()) },
                    // An empty confirm is a real answer here: it is how you
                    // clear the modular sets you had picked.
                ) { Text(stringResource(R.string.randomizer_choose_confirm)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Long enough that scrolling beats reading, short enough not to nag. */
private const val SEARCH_THRESHOLD = 10

private fun ChoiceOption.matches(query: String): Boolean {
    val needle = query.fold()
    return label.fold().contains(needle) || detail?.fold()?.contains(needle) == true
}

/**
 * Lower case and stripped of accents, so "machoire" finds "Mâchoire".
 *
 * Half the card names in French carry an accent and nobody types them into a
 * search box. Matching literally means the search works for the names that
 * need it least.
 */
private fun String.fold(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(ACCENTS, "")
        .lowercase()

private val ACCENTS = Regex("\\p{Mn}+")
