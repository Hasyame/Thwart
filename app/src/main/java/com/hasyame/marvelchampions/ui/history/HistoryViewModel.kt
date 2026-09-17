package com.hasyame.marvelchampions.ui.history

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.data.repository.PlayRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.ui.campaign.CampaignCovers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A game on the shelf: the play, and what stands for it: a bundled cover
 * when its campaign has one, the villain's card otherwise.
 */
data class PlayTile(
    val play: PlayEntity,
    val villainImageSrc: String?,
    @DrawableRes val coverRes: Int? = null,
)

/**
 * Every game played, newest first, each with its villain's face.
 *
 * The face is looked up per scenario rather than per play, since a table
 * that fought Rhino eleven times has one Rhino, and kept for the life of
 * the screen. A one-off game names its scenario by card set; a campaign's
 * names the scenario of its template, whose villain the campaign knows.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val plays: PlayRepository,
    private val cardDao: CardDao,
    private val campaigns: CampaignRepository,
    private val preferences: AppPreferences,
    val photoStore: PhotoStore,
) : ViewModel() {

    /** Villain image by scenario code, or by "run:scenario" for a campaign's. */
    private val faces = mutableMapOf<String, String?>()

    val tiles: StateFlow<List<PlayTile>> = plays.observePlays()
        .map { list ->
            val locale = preferences.currentCardLocale()
            list.map { play -> PlayTile(play, faceOf(play, locale), coverOf(play)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val message = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = message.asStateFlow()

    /** Which campaign a play belongs to, by template, cached per run. */
    private val templates = mutableMapOf<String, String?>()

    private suspend fun coverOf(play: PlayEntity): Int? {
        val runId = play.campaignRunId ?: return null
        val templateId = templates.getOrPut(runId) { campaigns.templateIdOf(runId) }
        return CampaignCovers.of(templateId)
    }

    private suspend fun faceOf(play: PlayEntity, locale: CardLocale): String? {
        val key = play.campaignRunId?.let { "$it:${play.scenarioCode}" } ?: play.scenarioCode
        if (key.isBlank()) {
            return null
        }
        return faces.getOrPut(key) {
            play.campaignRunId?.let { campaigns.villainFace(it, play.scenarioCode, locale) }
                ?: villainOfSet(play.scenarioCode, locale)
        }
    }

    /** The first villain of a scenario's set that has a picture, in the card language or English. */
    private suspend fun villainOfSet(setCode: String, locale: CardLocale): String? =
        listOf(locale, CardLocale.ENGLISH).distinct().firstNotNullOfOrNull { l ->
            cardDao.getScenarioSides(setCode, l.code)
                .firstOrNull { it.typeCode in VILLAIN_TYPES && it.imageSrc != null }
                ?.imageSrc
        }

    fun delete(playId: String) {
        viewModelScope.launch { plays.delete(playId) }
    }

    /** Sends a game that was not sent to BoardGameGeek when it was recorded. */
    fun report(playId: String) {
        viewModelScope.launch {
            message.value = when (val outcome = plays.report(playId)) {
                is PlayRecorded.SavedAndReported -> SENT
                is PlayRecorded.SavedReportFailed -> "Not sent to BoardGameGeek: ${outcome.detail}"
                else -> null
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SENT = "Sent to BoardGameGeek."

        /** Civil War's leaders sit in the villain's place, as the tracker has it. */
        val VILLAIN_TYPES = setOf("villain", "leader")
    }
}
