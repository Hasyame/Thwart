package com.hasyame.marvelchampions.ui.decks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The name you typed is the name you get.
 *
 * Reported by a player: a deck he had named came out called "Iron Man". The
 * cause was a prefill that overwrote the field whenever it looked empty, landing
 * after a database read — so a name typed while the hero's rules were loading
 * was read as blank and replaced by the hero's. The field is not "empty" while
 * somebody is typing into it; it is theirs.
 *
 * The second test is the one that keeps it fixed. The rule above is only safe
 * because every write to this screen's state is a compare-and-set, and a plain
 * `state.value = state.value.copy(...)` anywhere in the file brings the race
 * straight back — silently, and only for people who type quickly.
 */
class NewDeckNameTest {

    @Test
    fun `picking a hero fills an untouched field`() {
        val state = NewDeckUiState()

        assertEquals("Iron Man", state.namedAfterPicking("Iron Man").name)
    }

    @Test
    fun `picking a hero leaves a typed name alone`() {
        val state = NewDeckUiState(name = "Mon deck agressif", nameEdited = true)

        assertEquals("Mon deck agressif", state.namedAfterPicking("Iron Man").name)
    }

    @Test
    fun `changing hero does not rename a deck the player named`() {
        val state = NewDeckUiState(name = "Solo run", nameEdited = true)
            .namedAfterPicking("Iron Man")
            .namedAfterPicking("Spider-Man")

        assertEquals("Solo run", state.name)
    }

    @Test
    fun `a field cleared back to empty still counts as the player's`() {
        // Clearing it is a decision too, and the button stays disabled until
        // they write something. Refilling it from the hero behind their back is
        // how the original bug felt from the other side.
        val state = NewDeckUiState(name = "", nameEdited = true)

        assertEquals("", state.namedAfterPicking("Iron Man").name)
    }

    @Test
    fun `the state is only ever written by compare-and-set`() {
        val source = viewModelSource().readText()
        val assignments = source.lines()
            .map { it.trim() }
            .filter { it.startsWith("state.value =") }

        assertEquals(
            "a plain assignment here races with typing; use state.update { }",
            emptyList<String>(),
            assignments,
        )
    }

    private fun viewModelSource(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (candidate in listOf(SOURCE, SOURCE.removePrefix("app/"))) {
                val file = File(directory, candidate)
                if (file.isFile) {
                    // A guard that reads nothing passes by saying nothing.
                    assertTrue("$file is empty", file.length() > 0)
                    return file
                }
            }
            directory = directory.parentFile
        }
        error("could not find $SOURCE from ${File("").absolutePath}")
    }

    private companion object {
        const val SOURCE =
            "app/src/main/java/com/hasyame/marvelchampions/ui/decks/NewDeckViewModel.kt"
    }
}
