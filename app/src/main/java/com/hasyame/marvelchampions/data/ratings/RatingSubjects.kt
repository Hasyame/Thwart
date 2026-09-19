package com.hasyame.marvelchampions.data.ratings

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.domain.play.FearNoEvil
import com.hasyame.marvelchampions.domain.ratings.RatingSubject

fun RatingSubject.Companion.ofPlay(play: PlayEntity, scenarioSetCode: String? = play.scenarioCode): List<RatingSubject> {
    val scenario = scenarioSetCode?.takeIf { it.isNotBlank() } ?: return emptyList()
    // Fear No Evil played on its own names no set either.
    if (FearNoEvil.isFne(scenario)) {
        return emptyList()
    }
    val sets = play.modularSets.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return listOf(scenario(scenario)) + sets.map { modular(it, scenario) }
}
