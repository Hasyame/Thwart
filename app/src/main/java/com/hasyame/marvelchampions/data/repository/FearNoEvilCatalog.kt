package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.play.EncounterSetup
import com.hasyame.marvelchampions.domain.play.FearNoEvil
import com.hasyame.marvelchampions.domain.play.FearNoEvilBox
import com.hasyame.marvelchampions.domain.play.FneScenario
import com.hasyame.marvelchampions.domain.play.FneVillain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fear No Evil's box as the bundled template describes it, for the games
 * played outside the campaign. The reading is [FearNoEvilBox]'s; this only
 * finds the template.
 */
@Singleton
class FearNoEvilCatalog @Inject constructor(
    private val campaigns: CampaignRepository,
) {

    private suspend fun box(): FearNoEvilBox? =
        campaigns.bundledTemplates().firstOrNull { it.id == FearNoEvil.TEMPLATE_ID }?.let(::FearNoEvilBox)

    /** The pack the box is, for the collection to say whether it is owned. */
    suspend fun packCode(): String? = box()?.packCode

    suspend fun scenarios(locale: CardLocale): List<FneScenario> = box()?.scenarios(locale.code).orEmpty()

    suspend fun villains(locale: CardLocale): List<FneVillain> = box()?.villains(locale.code).orEmpty()

    suspend fun names(locale: CardLocale): Map<String, String> = box()?.names(locale.code).orEmpty()

    suspend fun encounterSetup(code: String, players: Int, expert: Boolean, locale: CardLocale): EncounterSetup =
        box()?.encounterSetup(code, players, expert, locale.code) ?: EncounterSetup()

    suspend fun briefing(code: String, difficulty: String?, locale: CardLocale): List<String> =
        box()?.briefing(code, difficulty, locale.code).orEmpty()
}
