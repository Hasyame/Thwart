package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.repository.CampaignDeckCard
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.PromptType
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate

/**
 * Page 3. The post-victory questionnaire, entirely driven by the template.
 *
 * Prompts whose `when` fails are not shown, which is how the Expert-only
 * questions disappear on Standard without this file knowing anything about
 * difficulty.
 */
@Composable
fun QuestionsPage(
    run: CampaignRun,
    isSubmitting: Boolean = false,
    scenario: ScenarioTemplate?,
    onCardClick: (String) -> Unit,
    onSubmit: (AnswerSet) -> Unit,
) {
    val context = EvaluationContext(state = run.state, scenarioId = scenario?.id)
    val prompts = scenario?.onVictory?.prompts.orEmpty()
        .filter { ConditionEvaluator.evaluate(it.condition, context) }

    val numbers = remember { mutableStateMapOf<String, String>() }
    val choices = remember { mutableStateMapOf<String, String>() }
    val cardLists = remember { mutableStateMapOf<String, String>() }
    val cardSelections = remember { mutableStateMapOf<String, Set<String>>() }
    val perHeroNumbers = remember { mutableStateMapOf<String, String>() }
    val perHeroBooleans = remember { mutableStateMapOf<String, Boolean>() }
    val perHeroCards = remember { mutableStateMapOf<String, Set<String>>() }

    // Every switch on the page starts recorded as "no". A map that only gains a
    // key when a switch is touched cannot tell "answered no" from "not asked",
    // and the log is meant to be a record of what the player said.
    val booleans = remember(prompts) {
        mutableStateMapOf<String, Boolean>().apply {
            prompts.filter { it.promptType == PromptType.BOOLEAN }
                .forEach { put(it.id, false) }
        }
    }

    // A choice the campaign words as "each player chooses" is compulsory: the
    // later scenarios are written assuming the card was taken. `min` on the
    // prompt is how a template says so, and until it is satisfied the page
    // cannot be filed.
    val unmetPrompts = prompts.filter { prompt ->
        val required = prompt.min ?: 0
        required > 0 &&
            when (prompt.promptType) {
                PromptType.CARD_SELECT, PromptType.DECK_CARD_SELECT ->
                    cardSelections[prompt.id].orEmpty().size < required

                // "Each player chooses one" is unmet while any one player has
                // not. A table of three where two have picked is not two thirds
                // done — the third player's deck is wrong for every scenario
                // that follows.
                PromptType.PER_HERO_CARD_SELECT -> run.state.heroes.any { hero ->
                    perHeroCards["${prompt.id}|${hero.id}"].orEmpty().size < required
                }

                else -> false
            }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_questions_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (prompts.isEmpty()) {
            // Silence here used to be indistinguishable from a broken screen,
            // so it now says which of the three reasons applies.
            Text(
                text = when {
                    scenario == null -> stringResource(R.string.campaign_no_scenario)
                    scenario.onVictory == null ->
                        stringResource(R.string.campaign_scenario_incomplete, scenario.id)

                    scenario.onVictory.prompts.isEmpty() ->
                        stringResource(R.string.campaign_scenario_incomplete, scenario.id)

                    else -> stringResource(R.string.campaign_no_questions)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }

        prompts.forEach { prompt ->
            // The card the app drew, named, and the campaign's own keywords set
            // apart the way the campaign sets them.
            // The card the app drew, named, so the question is about the thing
            // on the table rather than a generic one.
            val plainLabel = resolveDraws(
                prompt.label?.resolve(run.localeCode).orEmpty().ifBlank { prompt.id },
                run,
                run.state.currentScenarioId,
            )
            // The campaign's own keywords, set apart the way the campaign sets them.
            val label = campaignText(plainLabel)
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The cards the question is about, previewed the way a setup
                    // step previews its own. A question naming a card the player
                    // has to go and find on the table is a question they have to
                    // translate first.
                    // Both select types already list every card as a row of its
                    // own; previewing them again just prints the list twice.
                    val previews = prompt.cards
                        .takeIf {
                            prompt.promptType != PromptType.CARD_SELECT &&
                                prompt.promptType != PromptType.PER_HERO_CARD_SELECT
                        }
                        .orEmpty()

                    when (prompt.promptType) {
                        PromptType.NUMBER -> OutlinedTextField(
                            value = numbers[prompt.id].orEmpty(),
                            onValueChange = { numbers[prompt.id] = it.filter(Char::isDigit) },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PromptType.BOOLEAN -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f))
                            Switch(
                                checked = booleans[prompt.id] ?: false,
                                onCheckedChange = { booleans[prompt.id] = it },
                            )
                        }

                        PromptType.PER_HERO_CARD_SELECT -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            if ((prompt.min ?: 0) > 0) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.campaign_choice_required_each,
                                        prompt.min ?: 0,
                                        prompt.min ?: 0,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // One block per hero: the same card may be chosen
                            // twice at a table of two, which a shared list
                            // could not record.
                            run.state.heroes.forEach { hero ->
                                val key = "${prompt.id}|${hero.id}"
                                Text(
                                    text = hero.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                prompt.cards.forEach { code ->
                                    val selected = perHeroCards[key].orEmpty().contains(code)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(run.names.card(code), Modifier.weight(1f))
                                        Switch(
                                            checked = selected,
                                            onCheckedChange = { on ->
                                                val current = perHeroCards[key].orEmpty()
                                                perHeroCards[key] = if (on) {
                                                    current + code
                                                } else {
                                                    current - code
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        PromptType.PER_HERO_NUMBER -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            run.state.heroes.forEach { hero ->
                                val key = "${prompt.id}|${hero.id}"
                                OutlinedTextField(
                                    value = perHeroNumbers[key].orEmpty(),
                                    onValueChange = {
                                        perHeroNumbers[key] = it.filter(Char::isDigit)
                                    },
                                    label = { Text(hero.name) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        PromptType.PER_HERO_BOOLEAN -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            run.state.heroes.forEach { hero ->
                                val key = "${prompt.id}|${hero.id}"
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(hero.name, Modifier.weight(1f))
                                    Switch(
                                        checked = perHeroBooleans[key] ?: false,
                                        onCheckedChange = { perHeroBooleans[key] = it },
                                    )
                                }
                            }
                        }

                        PromptType.CARD_SELECT -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            if ((prompt.min ?: 0) > 0) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.campaign_choice_required,
                                        prompt.min ?: 0,
                                        prompt.min ?: 0,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Codes are recorded, names are shown, so a later
                            // scenario can act on the answer rather than only
                            // repeat it back.
                            prompt.cards.forEach { code ->
                                val selected = cardSelections[prompt.id].orEmpty().contains(code)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(run.names.card(code), Modifier.weight(1f))
                                    Switch(
                                        checked = selected,
                                        onCheckedChange = { on ->
                                            val current = cardSelections[prompt.id].orEmpty()
                                            cardSelections[prompt.id] = if (on) {
                                                current + code
                                            } else {
                                                current - code
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        PromptType.DECK_CARD_SELECT -> DeckCardField(
                            label = plainLabel,
                            deckCards = run.deckCards,
                            selected = cardSelections[prompt.id].orEmpty(),
                            onSelectionChange = { cardSelections[prompt.id] = it },
                        )

                        PromptType.CARD_LIST -> OutlinedTextField(
                            value = cardLists[prompt.id].orEmpty(),
                            onValueChange = { cardLists[prompt.id] = it },
                            label = { Text(label) },
                            supportingText = {
                                Text(stringResource(R.string.campaign_card_list_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PromptType.CHOICE -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                prompt.options.forEach { option ->
                                    FilterChip(
                                        selected = choices[prompt.id] == option.id,
                                        onClick = { choices[prompt.id] = option.id },
                                        label = {
                                            Text(
                                                option.label?.resolve(run.localeCode).orEmpty()
                                                    .ifBlank { option.id },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        PromptType.UNKNOWN -> Text(
                            text = stringResource(R.string.campaign_unknown_prompt, prompt.type),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (previews.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            previews.forEach { code ->
                                AssistChip(
                                    onClick = { onCardClick(code) },
                                    label = { Text(run.names.card(code)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (unmetPrompts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.campaign_answer_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            // Dead the instant it is tapped: filing a result writes to the
            // campaign log, records a play and may reach BoardGameGeek, and a
            // second tap would do all of it again.
            //
            // Also dead while a required choice is unmade. When the campaign
            // says each player chooses a card, that is not an offer — the
            // scenarios after it assume the card is in the deck, so letting the
            // page through unanswered would break the campaign quietly, several
            // scenarios later.
            enabled = !isSubmitting && unmetPrompts.isEmpty(),
            onClick = {
                onSubmit(
                    AnswerSet(
                        numbers = numbers.mapNotNull { (k, v) ->
                            v.toIntOrNull()?.let { k to it }
                        }.toMap(),
                        booleans = booleans.toMap(),
                        choices = choices.toMap(),
                        cardLists = cardLists.mapValues { (_, v) ->
                            v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        } + cardSelections.mapValues { (_, codes) -> codes.toList() },
                        perHeroNumbers = perHeroNumbers.entries
                            .mapNotNull { (key, value) ->
                                val parts = key.split('|')
                                val number = value.toIntOrNull()
                                if (parts.size == 2 && number != null) {
                                    Triple(parts[0], parts[1], number)
                                } else {
                                    null
                                }
                            }
                            .groupBy({ it.first }, { it.second to it.third })
                            .mapValues { entry -> entry.value.toMap() },
                        perHeroBooleans = perHeroBooleans.entries
                            .mapNotNull { (key, value) ->
                                val parts = key.split('|')
                                if (parts.size == 2) Triple(parts[0], parts[1], value) else null
                            }
                            .groupBy({ it.first }, { it.second to it.third })
                            .mapValues { entry -> entry.value.toMap() },
                        perHeroCards = perHeroCards.entries
                            .mapNotNull { (key, value) ->
                                val parts = key.split('|')
                                if (parts.size == 2) {
                                    Triple(parts[0], parts[1], value.toList())
                                } else {
                                    null
                                }
                            }
                            .groupBy({ it.first }, { it.second to it.third })
                            .mapValues { entry -> entry.value.toMap() },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (isSubmitting) R.string.campaign_validating else R.string.campaign_validate,
                ),
            )
        }
    }
}

/**
 * The chosen cards, with a button that opens the decks to change them.
 *
 * Listing every card of every deck inline buried the rest of the
 * questionnaire — two players is a hundred rows before the next question. The
 * page now shows only what was picked, and the list is somewhere you go.
 */
@Composable
private fun DeckCardField(
    label: String,
    deckCards: List<CampaignDeckCard>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.titleSmall)

    if (selected.isEmpty()) {
        Text(
            text = stringResource(R.string.campaign_no_cards_selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tapping a chip removes that card, so a mistake is undone where it
            // is seen rather than by reopening the list.
            selected.forEach { code ->
                val card = deckCards.firstOrNull { it.cardCode == code }
                FilterChip(
                    selected = true,
                    onClick = { onSelectionChange(selected - code) },
                    label = { Text(card?.cardName ?: code) },
                )
            }
        }
    }

    Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.campaign_choose_cards, selected.size))
    }

    if (picking) {
        DeckCardPickerSheet(
            deckCards = deckCards,
            selected = selected,
            onSelectionChange = onSelectionChange,
            onDismiss = { picking = false },
        )
    }
}

/**
 * The decks in play, grouped by the player who owns them.
 *
 * Grouping by hero is what lets two players say whose copy of a card it was,
 * which typing a title never could. The filter keeps a fifty-card deck usable
 * on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckCardPickerSheet(
    deckCards: List<CampaignDeckCard>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (deckCards.isEmpty()) {
                Text(
                    text = stringResource(R.string.campaign_no_deck_cards),
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text(stringResource(R.string.campaign_filter_cards)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val matching = deckCards.filter { it.cardName.contains(filter, ignoreCase = true) }
            // Chosen cards stay listed even when the filter would hide them, so
            // a selection cannot be lost behind a search term.
            val visible = (matching + deckCards.filter { it.cardCode in selected }).distinct()

            LazyColumn(Modifier.fillMaxWidth()) {
                visible.groupBy { it.heroId }.forEach { (heroId, cards) ->
                    item(key = "hero-$heroId") {
                        Text(
                            text = cards.first().heroName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(
                        items = cards.sortedBy { it.cardName },
                        key = { "$heroId-${it.cardCode}" },
                    ) { entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.cardName)
                                entry.typeName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = entry.cardCode in selected,
                                onCheckedChange = { on ->
                                    onSelectionChange(
                                        if (on) {
                                            selected + entry.cardCode
                                        } else {
                                            selected - entry.cardCode
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
