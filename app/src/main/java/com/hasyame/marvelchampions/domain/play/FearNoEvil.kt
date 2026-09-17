package com.hasyame.marvelchampions.domain.play

/**
 * Fear No Evil's scenarios, played on their own.
 *
 * The box's five jobs pair with whichever of its five subordinates is drawn
 * at the table; the finale is Kingpin himself. None of it is on MarvelCDB,
 * so the scenarios come from the campaign template rather than the card
 * database, and a one-off game names its scenario with a code of this
 * shape rather than a card set:
 *
 *   fne_s1_musee                        a job, villain still to draw
 *   fne_s1_musee__fne_villain_electro   the job with its villain
 *   fne_s6_caid                         the finale, whose villain is fixed
 *
 * The same shape the versus scenarios use for their two halves, so a play,
 * a replay and the history carry it without a column of their own. The web
 * client mirrors these codes, so a game recorded on one reads on the other.
 */
object FearNoEvil {

    const val TEMPLATE_ID = "fne"

    /** Every code of the family starts with this. */
    const val PREFIX = "fne_"

    /** Between the job and the villain, as the versus scenarios do it. */
    const val SEPARATOR = "__"

    fun isFne(code: String?): Boolean = code?.startsWith(PREFIX) == true

    /** The one-off code of a template scenario: `s1_musee` becomes `fne_s1_musee`. */
    fun codeOf(scenarioId: String): String = PREFIX + scenarioId

    fun compose(jobCode: String, villainId: String): String = jobCode + SEPARATOR + villainId

    /** The job's code and, when one has been drawn, the villain's id. */
    fun split(code: String): Pair<String, String?> {
        val at = code.indexOf(SEPARATOR)
        return if (at < 0) code to null else code.substring(0, at) to code.substring(at + SEPARATOR.length)
    }

    /** The template's scenario id, `s1_musee`, from either form of the code. */
    fun scenarioIdOf(code: String): String = split(code).first.removePrefix(PREFIX)

    /** A job with no villain on it yet: the table still has to draw one. */
    fun needsVillain(code: String?, villainChoices: Map<String, List<String>>): Boolean =
        code != null && isFne(code) && split(code).second == null && villainChoices[code].orEmpty().isNotEmpty()
}
