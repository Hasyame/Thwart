package com.hasyame.marvelchampions.domain.campaign

/** One scenario as the shared summary lists it. */
data class CampaignShareScenario(
    val name: String,
    val victory: Boolean,
    val time: String,
)

/**
 * The words around the figures, already in the reader's language.
 *
 * Passed in rather than looked up here, for the same reason the rest of this
 * package takes its text as arguments: a formatter that reaches for Android
 * resources cannot be tested without an Android runtime, and this one is
 * nothing but formatting decisions worth testing.
 */
data class CampaignShareLabels(
    val finished: String,
    val inProgress: String,
    val difficulty: String,
    val totalTime: String,
    val victoryPoints: String,
    val heroes: String,
    val scenariosPlayed: String,
    val wins: String,
    val defeats: String,
    val winRate: String,
    val cardsBought: String,
    val creditsLeft: String,
    val victory: String,
    val defeat: String,
    val footer: String,
)

/**
 * A finished campaign as text somebody can paste into a chat.
 *
 * A campaign is a dozen hours of somebody's evening spread over weeks, and the
 * end of one is the moment people want to show somebody. Plain text because
 * that is what survives being pasted — into Discord, a forum, a message — and
 * because it needs no rendering, no attachment and no network.
 *
 * The last line names the app. That is deliberate and it is the only place the
 * app talks about itself: somebody reading a friend's campaign summary is
 * exactly the person who would want it, and there is nowhere else this app is
 * advertised.
 */
object CampaignShareText {

    fun format(
        campaignName: String,
        templateName: String,
        difficulty: String,
        finished: Boolean,
        totalTime: String,
        victoryPoints: Int,
        heroNames: List<String>,
        scenariosWon: Int,
        scenariosLost: Int,
        winRatePercent: Int,
        hasMarket: Boolean,
        cardsBought: Int,
        creditsRemaining: Int,
        scenarios: List<CampaignShareScenario>,
        labels: CampaignShareLabels,
    ): String = buildString {
        // The run's own name when it has been given one, since that is what the
        // player calls it; the campaign's name otherwise. Both when they differ,
        // because "Second run at Kang" means nothing without the campaign.
        val title = campaignName.trim().takeIf { it.isNotBlank() && it != templateName }
            ?.let { "$it — $templateName" }
            ?: templateName
        appendLine(title)
        appendLine(if (finished) labels.finished else labels.inProgress)
        appendLine()

        line(labels.difficulty, difficulty)
        line(labels.totalTime, totalTime)
        line(labels.victoryPoints, victoryPoints.toString())
        line(labels.heroes, heroNames.joinToString(", "))
        line(labels.scenariosPlayed, (scenariosWon + scenariosLost).toString())
        line(labels.wins, scenariosWon.toString())
        line(labels.defeats, scenariosLost.toString())
        line(labels.winRate, "$winRatePercent%")
        // Only one campaign has a shop. For the others these were two lines of
        // nought, which is how the screen already treats them.
        if (hasMarket) {
            line(labels.cardsBought, cardsBought.toString())
            line(labels.creditsLeft, creditsRemaining.toString())
        }

        if (scenarios.isNotEmpty()) {
            appendLine()
            scenarios.forEach { scenario ->
                val outcome = if (scenario.victory) labels.victory else labels.defeat
                appendLine("${scenario.name} — $outcome · ${scenario.time}")
            }
        }

        appendLine()
        append(labels.footer)
    }.trim()

    /** A label and its value, or nothing at all when there is no value. */
    private fun StringBuilder.line(label: String, value: String) {
        if (value.isBlank()) {
            return
        }
        appendLine("$label: $value")
    }
}
