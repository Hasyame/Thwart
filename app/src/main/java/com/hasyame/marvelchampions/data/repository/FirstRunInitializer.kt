package com.hasyame.marvelchampions.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a new install assumes you own: the Core Set, and nothing else.
 *
 * The one box nobody plays this game without, which is the whole argument for
 * pre-ticking anything. It leaves the randomiser working on day one — three
 * scenarios, five heroes, Standard I and Expert I — while claiming nothing that
 * might be untrue, and the collection screen opens on first launch to be
 * corrected.
 *
 * This used to be the author's own fifteen packs, which shipped to everybody. A
 * player on a Galaxy Z Fold 7 reported heroes missing: the app was offering 23
 * of 65, because it believed he owned somebody else's collection. A default is
 * a claim about the person installing it, and the only honest claim is the box
 * the game cannot be played without.
 */
internal val PRESEEDED_COLLECTION: List<String> = listOf("core")

/** What the app needs to do before showing anything on first launch. */
enum class FirstRunOutcome {
    /** Database was already populated. Go to the normal start destination. */
    ALREADY_READY,

    /** Seeded from assets. Send the user to the collection screen to confirm it. */
    SEEDED,

    /** No bundled seed in this build. The user has to run a sync from Settings. */
    NEEDS_SYNC,
}

/**
 * Populates an empty install from the bundled seed.
 *
 * The collection is pre-filled at the same time, because an empty collection
 * makes the randomiser useless and the campaign tab unavailable — better to
 * offer a starting point the user corrects than an empty screen they have to
 * discover.
 */
@Singleton
class FirstRunInitializer @Inject constructor(
    private val cardDataRepository: CardDataRepository,
    private val collectionRepository: CollectionRepository,
) {

    suspend fun initialize(): FirstRunOutcome {
        val wasEmpty = cardDataRepository.isEmpty()
        if (!wasEmpty) {
            return FirstRunOutcome.ALREADY_READY
        }

        val seeded = cardDataRepository.seedIfEmpty()

        // Only ever pre-seed a collection the user has never touched, so a
        // deliberately emptied collection is not silently refilled.
        if (collectionRepository.isEmpty()) {
            collectionRepository.setOwnedBulk(PRESEEDED_COLLECTION, owned = true)
        }

        return if (seeded) FirstRunOutcome.SEEDED else FirstRunOutcome.NEEDS_SYNC
    }
}
