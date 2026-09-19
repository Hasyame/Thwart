package com.hasyame.marvelchampions.ui.achievements

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.achievements.AchievementCategory
import com.hasyame.marvelchampions.domain.achievements.DifficultyLevel
import com.hasyame.marvelchampions.domain.achievements.TierName

/**
 * The words for the achievements, keyed the way the shared specification
 * keys them (docs/spec/achievements/i18n.md): `achievement.<id>.title` and
 * `.description` become `achievement_<id>_title` and `_description`, so a
 * wording change never touches the definitions file. An id the strings do
 * not know is shown by its slug rather than hidden: the definitions may
 * grow before the words do.
 */
object AchievementTexts {

    private val titles: Map<String, Int> = mapOf(
        "beat_every_scenario_core" to R.string.achievement_beat_every_scenario_core_title,
        "beat_every_scenario_trors" to R.string.achievement_beat_every_scenario_trors_title,
        "beat_every_scenario_gmw" to R.string.achievement_beat_every_scenario_gmw_title,
        "beat_every_scenario_mts" to R.string.achievement_beat_every_scenario_mts_title,
        "beat_every_scenario_sm" to R.string.achievement_beat_every_scenario_sm_title,
        "beat_every_scenario_mut_gen" to R.string.achievement_beat_every_scenario_mut_gen_title,
        "beat_every_scenario_next_evol" to R.string.achievement_beat_every_scenario_next_evol_title,
        "beat_every_scenario_aoa" to R.string.achievement_beat_every_scenario_aoa_title,
        "beat_every_scenario_aos" to R.string.achievement_beat_every_scenario_aos_title,
        "beat_every_scenario_fne" to R.string.achievement_beat_every_scenario_fne_title,
        "beat_every_scenario" to R.string.achievement_beat_every_scenario_title,
        "win_with_every_hero_core" to R.string.achievement_win_with_every_hero_core_title,
        "win_with_every_hero" to R.string.achievement_win_with_every_hero_title,
        "win_all_aspects" to R.string.achievement_win_all_aspects_title,
        "rhino_all_aspects" to R.string.achievement_rhino_all_aspects_title,
        "first_expert_win" to R.string.achievement_first_expert_win_title,
        "beat_every_scenario_core_expert" to R.string.achievement_beat_every_scenario_core_expert_title,
        "beat_every_scenario_expert" to R.string.achievement_beat_every_scenario_expert_title,
        "plays_count" to R.string.achievement_plays_count_title,
        "wins_count" to R.string.achievement_wins_count_title,
        "heroes_played" to R.string.achievement_heroes_played_title,
        "distinct_days" to R.string.achievement_distinct_days_title,
        "true_solo_win" to R.string.achievement_true_solo_win_title,
        "two_player_win" to R.string.achievement_two_player_win_title,
        "three_player_win" to R.string.achievement_three_player_win_title,
        "four_player_win" to R.string.achievement_four_player_win_title,
        "four_player_four_aspects" to R.string.achievement_four_player_four_aspects_title,
        "finish_campaign" to R.string.achievement_finish_campaign_title,
        "finish_campaign_no_defeat" to R.string.achievement_finish_campaign_no_defeat_title,
        "finish_campaign_expert" to R.string.achievement_finish_campaign_expert_title,
        "draft_win" to R.string.achievement_draft_win_title,
        "draft_wins_5" to R.string.achievement_draft_wins_5_title,
        "draft_wins_10" to R.string.achievement_draft_wins_10_title,
        "draft_wins_50" to R.string.achievement_draft_wins_50_title,
        "sealed_win" to R.string.achievement_sealed_win_title,
        "sealed_wins_5" to R.string.achievement_sealed_wins_5_title,
        "sealed_wins_10" to R.string.achievement_sealed_wins_10_title,
        "sealed_wins_50" to R.string.achievement_sealed_wins_50_title,
        "losses_1" to R.string.achievement_losses_1_title,
        "losses_5" to R.string.achievement_losses_5_title,
        "losses_10" to R.string.achievement_losses_10_title,
        "losses_50" to R.string.achievement_losses_50_title,
        "losses_100" to R.string.achievement_losses_100_title,
    )

    /** The counting achievements' descriptions take the top threshold, and are plurals. */
    private val countDescriptions: Map<String, Int> = mapOf(
        "plays_count" to R.plurals.achievement_plays_count_description,
        "wins_count" to R.plurals.achievement_wins_count_description,
        "heroes_played" to R.plurals.achievement_heroes_played_description,
        "distinct_days" to R.plurals.achievement_distinct_days_description,
    )

    private val descriptions: Map<String, Int> = mapOf(
        "beat_every_scenario_core" to R.string.achievement_beat_every_scenario_core_description,
        "beat_every_scenario_trors" to R.string.achievement_beat_every_scenario_trors_description,
        "beat_every_scenario_gmw" to R.string.achievement_beat_every_scenario_gmw_description,
        "beat_every_scenario_mts" to R.string.achievement_beat_every_scenario_mts_description,
        "beat_every_scenario_sm" to R.string.achievement_beat_every_scenario_sm_description,
        "beat_every_scenario_mut_gen" to R.string.achievement_beat_every_scenario_mut_gen_description,
        "beat_every_scenario_next_evol" to R.string.achievement_beat_every_scenario_next_evol_description,
        "beat_every_scenario_aoa" to R.string.achievement_beat_every_scenario_aoa_description,
        "beat_every_scenario_aos" to R.string.achievement_beat_every_scenario_aos_description,
        "beat_every_scenario_fne" to R.string.achievement_beat_every_scenario_fne_description,
        "beat_every_scenario" to R.string.achievement_beat_every_scenario_description,
        "win_with_every_hero_core" to R.string.achievement_win_with_every_hero_core_description,
        "win_with_every_hero" to R.string.achievement_win_with_every_hero_description,
        "win_all_aspects" to R.string.achievement_win_all_aspects_description,
        "rhino_all_aspects" to R.string.achievement_rhino_all_aspects_description,
        "first_expert_win" to R.string.achievement_first_expert_win_description,
        "beat_every_scenario_core_expert" to R.string.achievement_beat_every_scenario_core_expert_description,
        "beat_every_scenario_expert" to R.string.achievement_beat_every_scenario_expert_description,
        "true_solo_win" to R.string.achievement_true_solo_win_description,
        "two_player_win" to R.string.achievement_two_player_win_description,
        "three_player_win" to R.string.achievement_three_player_win_description,
        "four_player_win" to R.string.achievement_four_player_win_description,
        "four_player_four_aspects" to R.string.achievement_four_player_four_aspects_description,
        "finish_campaign" to R.string.achievement_finish_campaign_description,
        "finish_campaign_no_defeat" to R.string.achievement_finish_campaign_no_defeat_description,
        "finish_campaign_expert" to R.string.achievement_finish_campaign_expert_description,
        "draft_win" to R.string.achievement_draft_win_description,
        "draft_wins_5" to R.string.achievement_draft_wins_5_description,
        "draft_wins_10" to R.string.achievement_draft_wins_10_description,
        "draft_wins_50" to R.string.achievement_draft_wins_50_description,
        "sealed_win" to R.string.achievement_sealed_win_description,
        "sealed_wins_5" to R.string.achievement_sealed_wins_5_description,
        "sealed_wins_10" to R.string.achievement_sealed_wins_10_description,
        "sealed_wins_50" to R.string.achievement_sealed_wins_50_description,
        "losses_1" to R.string.achievement_losses_1_description,
        "losses_5" to R.string.achievement_losses_5_description,
        "losses_10" to R.string.achievement_losses_10_description,
        "losses_50" to R.string.achievement_losses_50_description,
        "losses_100" to R.string.achievement_losses_100_description,
    )

    @StringRes
    fun title(id: String): Int? = titles[id]

    @StringRes
    fun description(id: String): Int? = descriptions[id]

    @PluralsRes
    fun countDescription(id: String): Int? = countDescriptions[id]

    @StringRes
    fun category(category: AchievementCategory): Int = when (category) {
        AchievementCategory.COVERAGE -> R.string.achievements_category_coverage
        AchievementCategory.DIFFICULTY -> R.string.achievements_category_difficulty
        AchievementCategory.VOLUME -> R.string.achievements_category_volume
        AchievementCategory.TABLE -> R.string.achievements_category_table
        AchievementCategory.CAMPAIGN -> R.string.achievements_category_campaign
        AchievementCategory.MODE -> R.string.achievements_category_mode
    }

    @StringRes
    fun tier(tier: TierName): Int = when (tier) {
        TierName.BRONZE -> R.string.achievements_tier_bronze
        TierName.SILVER -> R.string.achievements_tier_silver
        TierName.GOLD -> R.string.achievements_tier_gold
        TierName.PLATINUM -> R.string.achievements_tier_platinum
    }

    @StringRes
    fun level(level: DifficultyLevel): Int = when (level) {
        DifficultyLevel.UNKNOWN -> R.string.achievements_level_unknown
        DifficultyLevel.STANDARD -> R.string.achievements_level_standard
        DifficultyLevel.EXPERT -> R.string.achievements_level_expert
    }

    // --- the badge pictures --------------------------------------------------------------

    /**
     * A small picture for each achievement, the way a store shows a badge,
     * the same choices as the web's: a hero's card art for what is about
     * heroes, a villain's for what is about beating something, Fear No
     * Evil's bundled cover for its box. Two achievements may share a
     * picture; the title beside it tells them apart.
     */
    val heroById: Map<String, String> = mapOf(
        "win_with_every_hero_core" to "01001a",
        "win_with_every_hero" to "01010a",
        "win_all_aspects" to "04031a",
        "plays_count" to "01010a",
        "wins_count" to "01019a",
        "heroes_played" to "01029a",
        "distinct_days" to "01040a",
        "true_solo_win" to "01001a",
        "two_player_win" to "01010a",
        "three_player_win" to "01019a",
        "four_player_win" to "01029a",
        "four_player_four_aspects" to "04031a",
        "draft_win" to "05001a",
        "draft_wins_5" to "04031a",
        "draft_wins_10" to "01029a",
        "draft_wins_50" to "21031a",
        "sealed_win" to "04001a",
        "sealed_wins_5" to "16029a",
        "sealed_wins_10" to "01040a",
        "sealed_wins_50" to "09001a",
        "losses_1" to "01001a",
        "losses_5" to "05001a",
        "losses_10" to "35001a",
        "losses_50" to "34001a",
        "losses_100" to "03001a",
    )

    val scenarioById: Map<String, String> = mapOf(
        "beat_every_scenario" to "ultron",
        "beat_every_scenario_expert" to "ultron",
        "beat_every_scenario_core_expert" to "klaw",
        "rhino_all_aspects" to "rhino",
        "first_expert_win" to "klaw",
        "finish_campaign" to "crossbones",
        "finish_campaign_no_defeat" to "absorbing_man",
        "finish_campaign_expert" to "red_skull",
    )

    const val FALLBACK_HERO = "01001a"
}
