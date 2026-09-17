package com.hasyame.marvelchampions.ui.campaign

import androidx.annotation.DrawableRes
import com.hasyame.marvelchampions.R

/**
 * The box art a campaign is shown with, when the app carries it.
 *
 * Most campaigns are shown by their final villain's card, fetched like any
 * other card. Fear No Evil's villains are on no database, so its tile had
 * only a colour field; the box art is bundled for it instead, at the
 * author's decision, and stands for the campaign and for its games in the
 * history. Marvel's art, and the one picture of theirs the app ships.
 */
object CampaignCovers {

    /** The cover for a campaign template, or null to use the villain's card. */
    @DrawableRes
    fun of(templateId: String?): Int? = when (templateId) {
        "fne" -> R.drawable.campaign_cover_fne
        else -> null
    }
}
