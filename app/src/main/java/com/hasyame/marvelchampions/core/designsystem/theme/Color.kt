package com.hasyame.marvelchampions.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Red and gold: hot-rod lacquer over polished brass.
 *
 * The four chosen colours are the whole identity; everything else here is a
 * tint or shade derived from them so that Material's containers and disabled
 * states stay in the same family rather than drifting to stock purple.
 */

/** The primary red. Anything that acts is this colour. */
val IronRed = Color(0xFFE30022)
val IronRedDeep = Color(0xFFCC0000)
val IronRedTint = Color(0xFFFFD9D5)
val IronRedBright = Color(0xFFFF5A49)
val IronRedInk = Color(0xFF480007)

/** Brushed gold, for secondary emphasis. Warmer and duller than the highlight. */
val BrassGold = Color(0xFFD3AF37)
val BrassGoldTint = Color(0xFFFFE9AC)
val BrassGoldDeep = Color(0xFF8A7020)
val BrassGoldInk = Color(0xFF3A2D00)

/** The bright highlight. Reserved for accents, never a large fill. */
val ArcGold = Color(0xFFFCC200)
val ArcGoldTint = Color(0xFFFFE08A)
val ArcGoldDeep = Color(0xFF7A5F00)

/** Ink, for panel borders and outlines — what makes a card read as drawn. */
val PanelInk = Color(0xFF1A1113)
val PanelInkSoft = Color(0xFF534342)

/** Warm off-white rather than plain white, so the red is not glaring. */
val PaperWarm = Color(0xFFFFF8F6)
val PaperShade = Color(0xFFF3DDDB)

/**
 * Night pages, as five steps rather than two.
 *
 * Material needs a run of surfaces to place things at different depths; with
 * only a background and one raised tone, every card, sheet and dialog sat at
 * the same level and the screen read flat. Each step is warmed towards red so
 * the dark theme belongs to the same app as the light one rather than being
 * generic charcoal.
 */
val NightBase = Color(0xFF0D0809)
val NightLacquer = Color(0xFF120C0D)
val NightRaised = Color(0xFF1A1213)
val NightRaisedHigh = Color(0xFF241A1A)
val NightRaisedHighest = Color(0xFF2F2221)

/** Hairlines and borders on night pages. */
val NightOutline = Color(0xFFB9A6A3)
val NightOutlineSoft = Color(0xFF5A4A48)

/**
 * The drop shadow under a panel.
 *
 * Always dark, in both themes. Using the outline colour meant the dark theme
 * drew a pale slab behind every panel.
 */
val PanelShadow = Color(0xFF120C0B)

/**
 * Aspect colours, as the game prints them.
 *
 * Kept separate from the app palette above: these carry meaning a player
 * already knows, so they are not ours to restyle.
 */
/**
 * Neutral ink, for everything interactive.
 *
 * The app used gold here, which is the same colour the game prints Justice in —
 * a selected chip and a Justice card were the same yellow, one meaning "you
 * tapped this" and the other meaning "this is an aspect". Chrome in a neutral
 * leaves the aspect palette to mean only one thing, and lets the card art be
 * the colour on the screen.
 */
val InkGraphite = Color(0xFF2B2422)
val InkGraphiteSoft = Color(0xFFE4DAD6)
val BoneCream = Color(0xFFF2E7E2)
val BoneCreamDeep = Color(0xFF554A47)

/**
 * The cost pip, which is drawn over card art and so belongs to neither theme.
 */
val PipInk = Color(0xE60F0A0A)
val PipRim = Color(0xFFF2E7E2)

val AspectAggression = Color(0xFFC0392B)
val AspectJustice = Color(0xFFD4A017)
val AspectLeadership = Color(0xFF2E6DA4)
val AspectProtection = Color(0xFF3E8E5A)
val AspectBasic = Color(0xFF7A7A7A)

// --- card types -------------------------------------------------------------
// A deck list is skimmed, not read, so each type carries a colour as well as a
// shape. Picked to stay apart on the dark panels rather than to match the
// printed cards, whose aspect colours mean something else entirely.
val TypeAlly = Color(0xFF5B9BD5)
val TypeEvent = Color(0xFFE08A3C)
val TypeSupport = Color(0xFF5FAE72)
val TypeUpgrade = Color(0xFFA07CC9)
val TypeResource = Color(0xFFD3AF37)
val TypeObligation = Color(0xFFD65A5A)
val TypeOther = Color(0xFF9A8C8C)
