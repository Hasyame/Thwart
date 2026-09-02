package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.domain.model.BggPlay
import com.hasyame.marvelchampions.domain.model.BggPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A finished game, in the shape BoardGameGeek records one.
 *
 * BoardGameGeek has no win flag on a play, so the outcome and the details that
 * make the entry worth reading go in the comment.
 *
 * A top-level function rather than a method: it reads nothing but the play
 * itself, and hanging it off the repository meant it could not be tested
 * without standing up a database, an account and an HTTP client.
 */
internal fun PlayEntity.toBggPlay(bggUsername: String): BggPlay {
    val heroes = listOfNotNull(
        heroName.takeIf { it.isNotBlank() },
        otherHeroes.takeIf { it.isNotBlank() },
    ).joinToString(", ")

    // BoardGameGeek records a date and a length, and has no field for when
    // a game started or finished. The app knows both — playedAt is the
    // finish, elapsedMillis is how long it took — so they go in the comment,
    // which is the only place on a BGG play they can go.
    val finishedAt = Date(playedAt)
    val startedAt = Date(playedAt - elapsedMillis)

    val comment = buildString {
        append(if (won) "Win" else "Loss")
        append(" — ").append(scenarioName)
        append(" (").append(difficulty).append(')')
        if (heroes.isNotBlank()) {
            append("\nHeroes: ").append(heroes)
        }
        if (aspects.isNotBlank()) {
            append("\nAspects: ").append(aspects)
        }
        append("\nPlayed ").append(BggFormats.DATE_FORMAT.format(finishedAt))
        append(", ").append(BggFormats.TIME_FORMAT.format(startedAt))
        append("–").append(BggFormats.TIME_FORMAT.format(finishedAt))
        if (notes.isNotBlank()) {
            append('\n').append(notes)
        }
    }

    // One seat: the account holder, and nobody else.
    //
    // This briefly added a seat per hero, on the reasoning that BoardGameGeek
    // counts player rows as players and a group game was arriving as a solo
    // one. That confused heroes with people. Two-handed solo is one person
    // holding two decks, and it was filing a second player who does not exist;
    // at a real table the others have their own BGG accounts and log the game
    // themselves. Either way this is a record of what *this* person played.
    //
    // Nothing is lost by it: every hero at the table is named in the comment.
    val seats = listOf(
        BggPlayer(
            username = bggUsername,
            name = bggUsername,
            score = victoryPoints,
            won = won,
            color = listOfNotNull(
                heroName.takeIf { it.isNotBlank() },
                aspects.takeIf { it.isNotBlank() },
            ).joinToString(" / "),
        ),
    )

    return BggPlay(
        playedOn = BggFormats.DATE_FORMAT.format(Date(playedAt)),
        // BGG records length in minutes. A game shorter than a minute is
        // almost certainly a mistimed entry, so it reports as zero rather
        // than rounding up to something that looks deliberate.
        lengthMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis).toInt(),
        players = seats,
        won = won,
        comment = comment,
        location = location,
    )
}

private object BggFormats {
    // Fixed locale: this is a wire format for BoardGameGeek, not something
    // a person reads, so it must not follow the device language.
    val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Read by a person on BGG, unlike the date above, but fixed for the
    // same reason: a play reporting 14:05 to one reader and 2:05 PM to
    // another is describing the same game two ways.
    val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)
}
