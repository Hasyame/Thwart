package com.hasyame.marvelchampions.ui.achievements

import androidx.annotation.DrawableRes
import com.hasyame.marvelchampions.data.repository.AchievementRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.achievements.AchievementCategory
import com.hasyame.marvelchampions.domain.achievements.AchievementDefinition
import com.hasyame.marvelchampions.domain.achievements.AchievementState
import com.hasyame.marvelchampions.domain.achievements.AchievementStatusKind
import com.hasyame.marvelchampions.domain.achievements.Catalogue
import com.hasyame.marvelchampions.domain.achievements.Predicate
import com.hasyame.marvelchampions.domain.achievements.Progress
import com.hasyame.marvelchampions.domain.achievements.Tier
import com.hasyame.marvelchampions.domain.achievements.TierName
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.play.FearNoEvil
import com.hasyame.marvelchampions.ui.campaign.CampaignCovers
import javax.inject.Inject
import javax.inject.Singleton

/** The picture on a badge: a card's art on MarvelCDB, or a bundled cover. */
data class AchievementBadge(val imageSrc: String?, @DrawableRes val cover: Int?)

/** One achievement as a screen shows it: its status, and the words and picture beside it. */
data class AchievementCard(
    val id: String,
    val category: AchievementCategory,
    val status: AchievementStatusKind,
    val progress: Progress,
    val tier: TierName?,
    val tiers: List<Tier>,
    val unlockedAt: Long?,
    /** The pack a box achievement is about, by name, for its description. */
    val packName: String?,
    val badge: AchievementBadge,
) {
    val unlocked: Boolean get() = status == AchievementStatusKind.UNLOCKED
}

/**
 * Turns the derived state into cards a screen can show: the words come
 * from the strings by id, the pictures from the card data by the same
 * choices the web makes ([AchievementTexts.heroById] and friends). Shared
 * by the achievements page and the home page's strip.
 */
@Singleton
class AchievementPresenter @Inject constructor(
    private val repository: AchievementRepository,
    private val preferences: AppPreferences,
) {

    /** Villain picture by scenario key, looked up once per process. */
    private val scenarioFaces = HashMap<String, String?>()

    suspend fun cards(
        state: AchievementState,
        definitions: List<AchievementDefinition>,
        catalogue: Catalogue,
        names: AchievementRepository.Names,
    ): List<AchievementCard> {
        val locale = preferences.currentCardLocale()
        val statuses = state.achievements.associateBy { it.id }
        return definitions.mapNotNull { definition ->
            val status = statuses[definition.id] ?: return@mapNotNull null
            AchievementCard(
                id = definition.id,
                category = definition.category,
                status = status.status,
                progress = status.progress,
                tier = status.tier,
                tiers = definition.tiers,
                unlockedAt = status.unlockedAt,
                packName = packOf(definition)?.let { names.packs[it] ?: it },
                badge = badge(definition, catalogue, names, locale),
            )
        }
    }

    private fun packOf(definition: AchievementDefinition): String? = when (val p = definition.predicate) {
        is Predicate.ScenariosWon -> p.pack.takeIf { it != "*" }
        is Predicate.HeroesWon -> p.pack.takeIf { it != "*" }
        else -> null
    }

    private suspend fun badge(
        definition: AchievementDefinition,
        catalogue: Catalogue,
        names: AchievementRepository.Names,
        locale: CardLocale,
    ): AchievementBadge {
        AchievementTexts.heroById[definition.id]?.let { hero ->
            return AchievementBadge(names.heroFaces[hero], null)
        }
        var scenario = AchievementTexts.scenarioById[definition.id]
        val p = definition.predicate
        val pack = packOf(definition)
        if (scenario == null && pack != null) {
            if (pack == FearNoEvil.TEMPLATE_ID) {
                return AchievementBadge(null, CampaignCovers.of(FearNoEvil.TEMPLATE_ID))
            }
            scenario = catalogue.scenarios.firstOrNull { it.packCode == pack }?.key
        }
        if (scenario == null && p is Predicate.AspectsWon) {
            scenario = p.scenario
        }
        if (scenario != null) {
            val face = scenarioFaces.getOrPut(scenario) { repository.scenarioFace(scenario, locale) }
            return AchievementBadge(face, CampaignCovers.of(FearNoEvil.TEMPLATE_ID).takeIf { FearNoEvil.isFne(scenario) })
        }
        return AchievementBadge(names.heroFaces[AchievementTexts.FALLBACK_HERO], null)
    }
}
