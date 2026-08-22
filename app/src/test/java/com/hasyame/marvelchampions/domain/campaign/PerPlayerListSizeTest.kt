package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.Condition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "1 card per player or fewer", the limit Galaxy's Most Wanted pays a credit
 * for when it looks at The Collection.
 *
 * The rule is one check for the whole table. It used to be asked hero by hero,
 * which is a different rule and pays the wrong players as soon as the cards are
 * not spread evenly.
 */
class PerPlayerListSizeTest {

    private fun stateWith(cards: List<String>, heroes: Int) = CampaignState(
        heroes = (1..heroes).map {
            CampaignHero(id = "h$it", deckId = null, heroCardCode = "c$it", name = "H$it")
        },
        cardLists = mapOf("collection" to cards),
    )

    private val oneEachAtMost =
        Condition(cardList = "collection", maxSize = 1, perPlayer = true)

    private fun paysOut(cards: List<String>, heroes: Int) = ConditionEvaluator.evaluate(
        oneEachAtMost,
        EvaluationContext(stateWith(cards, heroes)),
    )

    @Test
    fun `the limit grows with the table`() {
        assertTrue(paysOut(listOf("a", "b"), heroes = 2))
        assertFalse(paysOut(listOf("a", "b", "c"), heroes = 2))
        assertTrue(paysOut(listOf("a", "b", "c", "d"), heroes = 4))
    }

    @Test
    fun `an uneven spread still pays, because the table is counted once`() {
        // Two players and two cards, both belonging to the same hero. The
        // booklet pays them both; asking each hero "have you at most 1?" paid
        // only the empty-handed one.
        assertTrue(paysOut(listOf("a", "b"), heroes = 2))
    }

    @Test
    fun `an empty collection is inside any limit`() {
        assertTrue(paysOut(emptyList(), heroes = 1))
        assertTrue(paysOut(emptyList(), heroes = 4))
    }

    @Test
    fun `without perPlayer the bound is taken literally`() {
        val flat = Condition(cardList = "collection", maxSize = 1)
        assertFalse(
            ConditionEvaluator.evaluate(flat, EvaluationContext(stateWith(listOf("a", "b"), 2))),
        )
    }

    @Test
    fun `a solo run is not divided by zero heroes`() {
        // Conditions are evaluated before the heroes are recorded in some
        // flows, and a limit of "1 per player" must not collapse to 0 there.
        val noHeroes = CampaignState(cardLists = mapOf("collection" to listOf("a")))
        assertTrue(ConditionEvaluator.evaluate(oneEachAtMost, EvaluationContext(noHeroes)))
    }
}
