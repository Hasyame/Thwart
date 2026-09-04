package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.backup.BackupSettings
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.DeckRepository

/**
 * What happens when a record arrives that this device already has a version of.
 *
 * The server applies last-write-wins to whole records and knows nothing about
 * what a play or a deck is. Three places where that is wrong are refined here,
 * on the client, which does know:
 *
 * - a play's `reportedToBgg`, where a stale `false` over a `true` posts a
 *   duplicate to BoardGameGeek — the one field in the app whose wrong merge
 *   escapes the app,
 * - a favourite's `addedAt`, where the earlier one is simply the true one,
 * - a campaign run's timer, which describes the device somebody is holding.
 *
 * And one where last-write-wins is not good enough at all: two people editing
 * the same imported deck. That forks instead of choosing.
 *
 * Everything here is a pure function over entities. No database, no clock, no
 * network — so the rules can be read in one place and tested without any of
 * them, which matters because these are the decisions that lose data when they
 * are wrong.
 */
object SyncMerge {

    /**
     * A play from the account, over one this device already had.
     *
     * `reportedToBgg` sticks at true once either side has posted the game.
     * Everything else is the incoming record: it is what the account says, and
     * a local edit that has not been pushed is not overwritten at all — the
     * engine keeps that one and pushes it instead.
     */
    fun play(incoming: PlayEntity, local: PlayEntity?): PlayEntity =
        if (local == null) {
            incoming
        } else {
            incoming.copy(reportedToBgg = incoming.reportedToBgg || local.reportedToBgg)
        }

    /**
     * A favourite, keeping the earlier `addedAt`.
     *
     * The card was in fact starred then, whichever device noticed first, and a
     * "favourites, oldest first" list that reshuffles itself after a sync is
     * reporting something that did not happen.
     */
    fun favourite(
        incoming: FavouriteCardEntity,
        local: FavouriteCardEntity?,
    ): FavouriteCardEntity = when {
        local == null -> incoming
        // Only while both are live. A tombstone has no starring date worth
        // preserving, and taking one would resurrect the earlier moment.
        local.deletedAt != null || incoming.deletedAt != null -> incoming
        else -> incoming.copy(addedAt = minOf(incoming.addedAt, local.addedAt))
    }

    /**
     * A campaign run, keeping this device's clock.
     *
     * The three timer columns never travel: they are excluded from the body on
     * the way out, so an incoming record carries whatever the entity's defaults
     * are rather than anything true. Taking them would have a phone pull
     * `timerRunningSince` from a tablet that is mid-game and start counting a
     * session nobody is playing.
     */
    fun campaignRun(incoming: CampaignRunEntity, local: CampaignRunEntity?): CampaignRunEntity =
        if (local == null) {
            incoming
        } else {
            incoming.copy(
                timerAccumulatedMillis = local.timerAccumulatedMillis,
                timerRunningSince = local.timerRunningSince,
                timerScenarioId = local.timerScenarioId,
            )
        }

    /**
     * Owning a pack on either device means owning it.
     *
     * Used on a first merge and a full resync, where there is no history to
     * adjudicate with and the safe direction is inclusive. Afterwards a pack
     * is plain last-write-wins: by then a lower number is somebody correcting
     * the count, not a device that never heard about the second copy.
     */
    fun ownedPackOnFirstMerge(
        incoming: OwnedPackEntity,
        local: OwnedPackEntity?,
    ): OwnedPackEntity = when {
        local == null -> incoming
        local.deletedAt != null -> incoming
        incoming.deletedAt != null -> local
        else -> incoming.copy(quantity = maxOf(incoming.quantity, local.quantity))
    }

    /**
     * True when two devices have edited the same imported deck differently.
     *
     * The only genuinely lossy conflict in the system. Two devices importing
     * MarvelCDB decklist 12345 both produce `decklist-12345`, which is a happy
     * accident: the import is idempotent and the table gets one deck. It stops
     * being happy when both have been edited since, because then
     * last-write-wins throws away somebody's deckbuilding.
     *
     * Narrow on purpose: one collection, one field pair, and a situation that
     * takes deliberate offline editing on two devices to reach.
     */
    fun deckForks(incoming: SavedDeckEntity, local: SavedDeckEntity?): Boolean =
        local != null &&
            local.deletedAt == null &&
            incoming.deletedAt == null &&
            local.locallyEdited &&
            incoming.locallyEdited &&
            local.slots != incoming.slots

    /**
     * The local deck, re-keyed so both survive.
     *
     * The incoming one keeps the shared id, because that is the one the account
     * agrees on and the one another device will import again. This copy becomes
     * a local deck like any other, marked dirty by the caller so it uploads as
     * a new deck rather than sitting on one phone.
     *
     * The name is suffixed so the two are told apart in a list where they are
     * otherwise identical. Nothing else changes: the slots, the description and
     * the raw import are the player's work.
     */
    fun forkedDeck(local: SavedDeckEntity, newId: String, suffix: String): SavedDeckEntity =
        local.copy(
            id = newId,
            name = "${local.name} $suffix".trim(),
            // It is no longer the deck that was imported under that number, so
            // it does not claim to be: a later import of 12345 must not think
            // this row is it.
            marvelCdbId = 0,
            kind = DeckRepository.LOCAL_KIND,
            url = "",
        )

    /**
     * The preferences, one key at a time.
     *
     * Last-write-wins per key rather than per record, so two devices changing
     * two different settings both keep theirs. `dismissedPacks` is the
     * exception and unions: a pack turned down anywhere has been turned down,
     * and re-offering it on the other phone is the nagging the dismissal
     * existed to stop.
     *
     * [preferLocal] is for the first merge, where there is no history to
     * adjudicate with and the device in front of the person is the better
     * guess at what they want to look at.
     */
    fun settings(
        incoming: BackupSettings,
        local: BackupSettings,
        preferLocal: Boolean,
    ): BackupSettings {
        fun pick(incomingValue: String, localValue: String): String = when {
            preferLocal && localValue.isNotBlank() -> localValue
            incomingValue.isNotBlank() -> incomingValue
            else -> localValue
        }
        return BackupSettings(
            cardLocale = pick(incoming.cardLocale, local.cardLocale),
            themeChoice = pick(incoming.themeChoice, local.themeChoice),
            playLocation = pick(incoming.playLocation, local.playLocation),
            trackEncounter = if (preferLocal) local.trackEncounter else incoming.trackEncounter,
            dismissedPacks = (incoming.dismissedPacks + local.dismissedPacks).distinct().sorted(),
        )
    }
}
