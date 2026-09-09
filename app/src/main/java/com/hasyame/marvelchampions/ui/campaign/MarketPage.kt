package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.MarketRules
import com.hasyame.marvelchampions.domain.campaign.engine.PurchaseRefusal

/**
 * Page 4. The market, laid out like a shop: a grid of card images with prices.
 *
 * Tapping a card zooms it; buying asks for confirmation, because a purchase
 * spends credits that cannot be re-earned and is only reversible through the
 * refund below.
 */
@Composable
fun MarketPage(
    run: CampaignRun,
    onBuy: (String, String, Int, String) -> Unit,
    onRefund: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onDone: () -> Unit,
) {
    val market = run.template.market
    if (market == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.campaign_no_market))
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.action_back)) }
        }
        return
    }

    // Credits are per hero, so the shop is browsed as one player at a time.
    var buyerId by remember(run.state.heroes) {
        mutableStateOf(run.state.heroes.firstOrNull()?.id.orEmpty())
    }
    var zoomed by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }

    val offers = MarketRules.offersFor(run.template, run.state, buyerId)
    val credits = run.state.heroCounter(market.counterId, buyerId)

    Column(Modifier.fillMaxSize()) {
        // The shopfront: who is buying and what they can spend, boxed so it
        // reads over the halftone and separates from the goods below.
        ComicPanel(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.campaign_market),
                    style = MaterialTheme.typography.headlineSmall,
                )
                // Always shown, even solo: a purchase spends one specific
                // player's units, so who is buying must never be a guess.
                Text(
                    text = stringResource(R.string.campaign_who_is_buying),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    run.state.heroes.forEachIndexed { index, hero ->
                        FilterChip(
                            selected = hero.id == buyerId,
                            onClick = { buyerId = hero.id },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.campaign_player_label,
                                        index + 1,
                                        hero.name,
                                        run.state.heroCounter(market.counterId, hero.id),
                                    ),
                                )
                            },
                        )
                    }
                }
                // The purse, in gold and large: it is the number every decision
                // on this screen is measured against.
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.campaign_credits_available,
                            credits,
                            credits,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(offers, key = { it.entry.cardCode }) { offer ->
                val code = offer.entry.cardCode
                Card(
                    modifier = Modifier.clickable { zoomed = code },
                    colors = if (offer.canBuy) {
                        CardDefaults.cardColors()
                    } else {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    },
                ) {
                    Column {
                        AsyncImage(
                            model = MarvelCdbUrls.cardImage(run.imageSrc(code)),
                            contentDescription = run.names.card(code),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                        )
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                text = run.names.card(code),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = pluralStringResource(R.plurals.campaign_price, offer.entry.cost, offer.entry.cost),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (offer.canBuy) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            offer.refusal?.let {
                                Text(
                                    text = refusalLabel(it, run),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (run.state.purchases.isNotEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.campaign_purchased),
                    style = MaterialTheme.typography.titleSmall,
                )
                run.state.purchases.forEach { purchase ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = run.names.card(purchase.cardCode) + " — " +
                                (run.state.heroes.firstOrNull { it.id == purchase.heroId }?.name
                                    ?: purchase.heroId),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onRefund(purchase.eventId) }) {
                            Text(stringResource(R.string.campaign_refund))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(stringResource(R.string.campaign_done_shopping)) }
    }

    zoomed?.let { code ->
        val offer = offers.firstOrNull { it.entry.cardCode == code }
        AlertDialog(
            onDismissRequest = { zoomed = null },
            title = { Text(run.names.card(code)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsyncImage(
                        model = MarvelCdbUrls.cardImage(run.imageSrc(code)),
                        contentDescription = run.names.card(code),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = pluralStringResource(R.plurals.campaign_price, offer?.entry?.cost ?: 0, offer?.entry?.cost ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    offer?.refusal?.let {
                        Text(
                            text = refusalLabel(it, run),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    TextButton(onClick = { onCardClick(code) }) {
                        Text(stringResource(R.string.campaign_see_card))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirming = code },
                    enabled = offer?.canBuy == true,
                ) { Text(stringResource(R.string.campaign_buy)) }
            },
            dismissButton = {
                TextButton(onClick = { zoomed = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    confirming?.let { code ->
        val offer = offers.firstOrNull { it.entry.cardCode == code }
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.campaign_are_you_sure)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.campaign_confirm_purchase,
                        offer?.entry?.cost ?: 0,
                        run.names.card(code),
                        offer?.entry?.cost ?: 0,
                        // Naming the buyer here is the last chance to notice
                        // the wrong player is selected.
                        run.state.heroes.firstOrNull { it.id == buyerId }?.name.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        offer?.let {
                            onBuy(buyerId, code, it.entry.cost, it.entry.cardListId)
                        }
                        confirming = null
                        zoomed = null
                    },
                ) { Text(stringResource(R.string.campaign_buy)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun refusalLabel(refusal: PurchaseRefusal, run: CampaignRun): String = when (refusal) {
    is PurchaseRefusal.AlreadyOwnedByGroup -> stringResource(
        R.string.campaign_already_bought,
        run.state.heroes.firstOrNull { it.id == refusal.heroId }?.name ?: refusal.heroId,
    )

    PurchaseRefusal.NotEnoughCredits -> stringResource(R.string.campaign_not_enough_credits)
    PurchaseRefusal.NoMarket -> stringResource(R.string.campaign_no_market)
    PurchaseRefusal.UnknownCard -> stringResource(R.string.campaign_unknown_card)
}
