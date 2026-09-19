package com.hasyame.marvelchampions.domain.model

/**
 * Which theme the app uses, as chosen in Settings.
 *
 * New installations are initialized to [SYSTEM] by AppPreferences. [DARK]
 * remains the fallback for legacy/invalid stored codes, preserving old installs.
 */
enum class ThemeChoice(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): ThemeChoice =
            entries.firstOrNull { it.code == code } ?: DARK
    }
}
