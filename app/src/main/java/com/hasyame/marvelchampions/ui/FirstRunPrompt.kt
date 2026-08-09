package com.hasyame.marvelchampions.ui

/**
 * Answers "open the collection screen now?" — true at most once.
 *
 * A separate object with a name, rather than a boolean inside the view model,
 * because getting this wrong is not a cosmetic bug and it is not obvious from
 * reading the call site. The composition that acts on the answer is rebuilt on
 * every configuration change while the view model holding it is not, so a flag
 * that is merely *read* is re-read on every fold, unfold and rotation. On a
 * Galaxy Z Fold 7 that is every time the phone is opened.
 *
 * Not thread safe, and does not need to be: it is consumed from the main thread
 * during composition.
 */
class FirstRunPrompt {

    private var shown = false

    /**
     * True the first time [needed] is true, and false forever after.
     *
     * [needed] is passed in rather than stored so the caller stays the one
     * deciding what a first run is; this only remembers that it has answered.
     */
    fun consume(needed: Boolean): Boolean {
        if (!needed || shown) {
            return false
        }
        shown = true
        return true
    }
}
