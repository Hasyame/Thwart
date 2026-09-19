package com.hasyame.marvelchampions.ui.home

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.repository.AchievementRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.achievements.AchievementDerivation
import com.hasyame.marvelchampions.ui.achievements.AchievementCard
import com.hasyame.marvelchampions.ui.achievements.AchievementPresenter
import com.hasyame.marvelchampions.data.sync.SyncSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val versionName: String = "",
    val versionCode: Int = 0,
    /** This version's release notes, in the app's language, or null when the file is absent. */
    val notes: String? = null,
    /** True once the notes for this version have been put away. */
    val notesDismissed: Boolean = false,
    /** A card drawn at random, set for the screen to open and then cleared. */
    val randomCardCode: String? = null,
    /** The pseudonym of the account signed in on this phone, or null when none is. */
    val accountHandle: String? = null,
)

/**
 * The home page's few facts: which version this is and what it changed,
 * read from the notes bundled at build time, so nothing is fetched.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val cardDao: CardDao,
    private val sessions: SyncSessionStore,
    private val achievements: AchievementRepository,
    private val presenter: AchievementPresenter,
) : ViewModel() {

    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    /**
     * The achievements as a store's front page shows them: the count, the
     * latest earned, a row of badges. Read from the history like the page
     * itself; null while the definitions are refused, and nothing is shown.
     */
    val achievementStrip: StateFlow<HomeAchievements?> = achievements.observeInput()
        .map { input ->
            val derived = input?.let(AchievementDerivation::derive) ?: return@map null
            val cards = presenter.cards(derived, input.definitions, input.catalogue, achievements.names())
            val byId = cards.associateBy { it.id }
            HomeAchievements(
                unlocked = cards.count { it.unlocked },
                total = cards.size,
                latest = derived.recent.firstOrNull()?.let { byId[it.id] },
                unlockedCards = derived.recent.mapNotNull { byId[it.id] },
                lockedCards = cards.filterNot { it.unlocked },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(info).toInt()
        state.update {
            it.copy(
                versionName = info.versionName.orEmpty(),
                versionCode = code,
                notes = readNotes(code),
            )
        }
        preferences.dismissedNotesVersion
            .onEach { dismissed -> state.update { it.copy(notesDismissed = dismissed >= code) } }
            .launchIn(viewModelScope)
        sessions.session
            .onEach { session ->
                state.update { it.copy(accountHandle = session.handle.takeIf { session.isSignedIn }) }
            }
            .launchIn(viewModelScope)
    }

    fun dismissNotes() {
        viewModelScope.launch { preferences.setDismissedNotesVersion(state.value.versionCode) }
    }

    /** One card, any card: a small pleasure the reference app has, and cheap. */
    fun drawRandomCard() {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            state.update { it.copy(randomCardCode = cardDao.randomPlayerCard(locale.code)?.code) }
        }
    }

    fun consumeRandomCard() = state.update { it.copy(randomCardCode = null) }

    /**
     * The changelog fastlane keeps for this version, in the app's language
     * where it exists and in English otherwise; null when neither does, as
     * on a build that skipped the notes.
     */
    private fun readNotes(versionCode: Int): String? {
        val language = Locale.getDefault().language
        val folders = listOfNotNull(FASTLANE_LOCALES[language], FASTLANE_LOCALES.getValue("en"))
        folders.forEach { folder ->
            try {
                return context.assets.open("changelogs/$folder/changelogs/$versionCode.txt")
                    .bufferedReader().use { it.readText() }
                    .let(::reflow)
            } catch (_: FileNotFoundException) {
                // The next language, then none.
            }
        }
        return null
    }

    /**
     * The files are wrapped at a fixed width for the stores; on a phone the
     * width is the screen's. Lines inside a paragraph are joined, and the
     * blank lines between paragraphs kept.
     */
    private fun reflow(text: String): String = text.trim()
        .split(PARAGRAPH_BREAK)
        .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }

    private companion object {
        /** The app's languages, as fastlane names their folders. */
        val FASTLANE_LOCALES = mapOf("en" to "en-US", "fr" to "fr-FR")

        /** A blank line, with or without spaces on it. */
        val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n")
    }
}

/** What the home page says about the achievements. */
data class HomeAchievements(
    val unlocked: Int,
    val total: Int,
    /** The most recently earned, shown with its words. */
    val latest: AchievementCard?,
    /** Earned, newest first. */
    val unlockedCards: List<AchievementCard>,
    /** Not yet, in the file's order. */
    val lockedCards: List<AchievementCard>,
)
