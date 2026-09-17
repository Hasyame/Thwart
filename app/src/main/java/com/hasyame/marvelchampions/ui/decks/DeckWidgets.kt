package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.ui.util.aspectLabel

/*
 * The pieces the deck screens are built of, shared so the shelf, the deck
 * page and the editor read as one place. Drawn after the web client's deck
 * pages, which is where the look was settled first.
 */

/** A printed card is 63 by 88 mm. */
internal const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * Where a crop of a card lands: below the title band, on the art. A card's
 * name box takes the top eighth or so; centring the crop a little under it
 * shows the face rather than the lettering.
 */
private val ART_FOCUS = BiasAlignment(0f, -0.55f)

/**
 * The hero's art as a banner, the name written over its darker foot.
 *
 * The picture is the card's, cropped to its upper part where the face is;
 * a card that has no picture yet gets a plain dark band, so the name is in
 * the same place either way.
 */
@Composable
fun HeroBanner(
    imageSrc: String?,
    title: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    chips: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        MarvelCdbUrls.cardImage(imageSrc)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = ART_FOCUS,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            chips?.invoke()
        }
    }
}

/** A small rounded label: the hero, a card count, a folder. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = tint ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** An aspect, with its colour as a dot before the word. */
@Composable
fun AspectPill(aspect: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AspectDot(aspect)
        Text(aspectLabel(aspect), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AspectDot(aspect: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(aspectColor(aspect) ?: MaterialTheme.colorScheme.outline),
    )
}

/** The chips under a deck's name: hero, aspects, card count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckChips(heroName: String, aspects: List<String>, cardCount: Int, legal: Boolean? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(heroName)
        aspects.forEach { AspectPill(it) }
        Pill(androidx.compose.ui.res.pluralStringResource(R.plurals.decks_card_count, cardCount, cardCount))
        legal?.let { LegalBadge(it) }
    }
}

/** "LEGAL" or "NOT LEGAL", as a small stamp. */
@Composable
fun LegalBadge(legal: Boolean, modifier: Modifier = Modifier) {
    val colour = if (legal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
    Text(
        stringResource(if (legal) R.string.decks_legal_short else R.string.decks_illegal_short).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = colour,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, colour, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A number in a small circle: the copies of a card, or its cost. */
@Composable
fun Counter(value: String, modifier: Modifier = Modifier, emphasised: Boolean = false) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasised) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One card in a list: how many, a bar in the aspect's colour, the name, a
 * line under it, and the cost at the end. [trailing] takes the cost's place
 * when the row has controls of its own, as in the editor.
 */
@Composable
fun DeckCardRow(
    card: CardEntity,
    quantity: Int?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleIsWarning: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        quantity?.let { Counter(it.toString()) }
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(aspectColor(card.factionCode) ?: MaterialTheme.colorScheme.outlineVariant),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.isUnique) {
                    Text("◆", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (subtitleIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            card.cost?.let { Counter(it.toString()) }
        }
    }
}

/** A card as its picture, for the grid view, with the copies in the corner. */
@Composable
fun CardTile(card: CardEntity, quantity: Int?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MarvelCdbUrls.cardImage(card.imageSrc)?.let { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } ?: Text(
                card.name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            quantity?.let {
                Counter(it.toString(), Modifier.align(Alignment.TopEnd).padding(6.dp), emphasised = true)
            }
        }
        Text(card.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The hero at the head of a deck: portrait, name, hand size and hit points. */
@Composable
fun HeroCard(hero: CardEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MarvelCdbUrls.cardImage(hero.imageSrc)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = ART_FOCUS,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.decks_hero).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(hero.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        hero.handSize?.let { Stat(stringResource(R.string.decks_hand_size), it.toString()) }
        hero.health?.let { Stat(stringResource(R.string.decks_hit_points), it.toString()) }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A line that reads as a finding: a tick or a cross, then the words. */
@Composable
fun Verdict(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (ok) "✓" else "✗",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

/** A section heading with its count beside it: "ALLY 7". */
@Composable
fun GroupHeading(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        count?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
