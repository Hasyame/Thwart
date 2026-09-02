package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The number printed beside a bar, and how full the bar is drawn.
 *
 * These are not always the same quantity, which is the whole reason this
 * arithmetic exists and the reason it went wrong. A rate is a proportion of one
 * row's own games. A share is a proportion of every row together, drawn against
 * the largest. Losses were added as a rate and fell through to the share
 * branch, so a hero who lost half his games was reported as some fraction of
 * everybody's losses put together — a smaller, plausible-looking number that
 * nobody would question.
 *
 * Worth testing rather than looking at, because a wrong percentage looks
 * exactly like a right one.
 */
class StatMeasureTest {

    private fun row(played: Int, won: Int, millis: Long = 0) =
        WinRateRow(key = "k", played = played, won = won, totalMillis = millis)

    @Test
    fun `a loss rate is this row's own games, not a share of everyone's`() {
        val half = row(played = 10, won = 5)
        // Two other rows, so a share would divide by something much larger.
        val total = 0.5 + 0.9 + 0.1
        val largest = 0.9

        val (label, bar) = measure(half, StatMetric.LOSS_RATE, total, largest)

        assertEquals("50%", label)
        assertEquals(0.5f, bar, 0.0001f)
    }

    @Test
    fun `wins and losses are the two halves of the same row`() {
        val r = row(played = 8, won = 6)

        assertEquals(0.75, rawValue(r, StatMetric.WIN_RATE), 0.0001)
        assertEquals(0.25, rawValue(r, StatMetric.LOSS_RATE), 0.0001)
    }

    @Test
    fun `a row with no games is not a hundred percent defeats`() {
        // The reason losses are counted over games played rather than as one
        // minus the win rate: the subtraction would call an empty row a total
        // loss, which is a claim about games nobody played.
        val none = row(played = 0, won = 0)

        assertEquals(0.0, rawValue(none, StatMetric.LOSS_RATE), 0.0001)
        assertEquals("0%", measure(none, StatMetric.LOSS_RATE, 0.0, 0.0).first)
    }

    @Test
    fun `a share is still a share`() {
        // The branch losses were wrongly taking. Games played is not a
        // proportion of anything on its own, so it prints its share of the
        // whole and draws itself against the biggest row.
        val r = row(played = 25, won = 10)

        val (label, bar) = measure(r, StatMetric.SHARE_PLAYED, total = 100.0, largest = 50.0)

        assertEquals("25%", label)
        assertEquals(0.5f, bar, 0.0001f)
    }

    @Test
    fun `every measure a box offers is one of the two shapes`() {
        // A fifth measure added later has to declare which it is. Without this
        // it would default into the share branch exactly as losses did, and be
        // wrong in the same quiet way.
        val rows = listOf(
            row(played = 4, won = 3, millis = 1_000L),
            row(played = 6, won = 1, millis = 500L),
        )

        StatMetric.entries.forEach { metric ->
            // Totals taken from the metric on show, as the screen takes them:
            // a bar is only comparable to the others measuring the same thing.
            val total = rows.sumOf { rawValue(it, metric) }
            val largest = rows.maxOf { rawValue(it, metric) }
            rows.forEach { r ->
                val (label, bar) = measure(r, metric, total, largest)
                assert(label.endsWith("%")) { "$metric printed $label" }
                assert(bar in 0f..1f) { "$metric drew a bar of $bar" }
            }
        }
    }
}
