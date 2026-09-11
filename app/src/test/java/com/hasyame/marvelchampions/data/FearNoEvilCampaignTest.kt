package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.allSetupSteps
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * A whole Peur de Rien campaign, played against the template that ships.
 *
 * The unit tests around the engine use a hand-built template, which is exactly
 * where a mistake in the real one hides. This drives the shipped file end to
 * end: five jobs, one rotation each, and the boss only after all five.
 */
@RunWith(RobolectricTestRunner::class)
class FearNoEvilCampaignTest {

    private val engine = CampaignEngine()

    // One instance: building a Json format per call is slow and the linter
    // says so.
    private val json = Json { ignoreUnknownKeys = true }

    private fun template(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/fne.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text).expanded()
    }

    private var clock = 0L
    private fun tick() = ++clock

    private fun start(template: CampaignTemplate) = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = tick(),
        templateId = template.id,
        difficulty = "standard",
        heroes = listOf(
            CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Hero"),
        ),
        startScenarioId = template.scenarios.first().id,
    )

    private fun offer(vararg ids: String) = CampaignEvent.EnvironmentsOffered(
        id = "offer-${tick()}",
        timestamp = clock,
        offered = ids.toList(),
    )

    private fun keep(id: String) = CampaignEvent.EnvironmentChosen(
        id = "keep-${tick()}",
        timestamp = clock,
        environmentId = id,
    )

    private fun play(id: String) = listOf(
        CampaignEvent.ScenarioChosen(id = "choose-${tick()}", timestamp = clock, scenarioId = id),
        CampaignEvent.ScenarioCompleted(
            id = "done-${tick()}",
            timestamp = clock,
            scenarioId = id,
            victory = true,
        ),
    )

    private fun choosable(template: CampaignTemplate, state: CampaignState) =
        CampaignEngine.choosableScenarios(template, state).map { it.id }

    @Test
    fun `pressure adds up across rotations`() {
        val template = template()
        // The same place hit in two different rotations is at two, not one.
        val state = engine.fold(
            template,
            listOf(start(template)) +
                offer("s1_musee", "s2_poursuite") + keep("s2_poursuite") +
                play("s2_poursuite") +
                offer("s1_musee", "s3_racket") + keep("s3_racket"),
        )

        assertEquals("hit in both rotations", 2, state.counter("pressionMusee"))
        assertEquals(1, state.counter("pressionRacket"))
        assertFalse(state.campaignLost)
    }

    @Test
    fun `a job already seen through cannot fall`() {
        val template = template()
        // Three hits on a place the players already settled. It is done, and a
        // campaign must not end over a number beside a job nobody could still
        // have saved.
        val state = engine.fold(
            template,
            listOf(start(template)) +
                offer("s1_musee") + keep("s1_musee") + play("s1_musee") +
                offer("s1_musee") + keep("s1_musee"),
        )

        assertEquals(3, state.counter("pressionMusee"))
        assertFalse("a settled job took the campaign down", state.campaignLost)
        assertFalse(state.finished)
    }

    @Test
    fun `the boss waits until every job is done`() {
        val template = template()
        val jobs = listOf("s1_musee", "s2_poursuite", "s3_racket", "s4_raft", "s5_rotatives")

        var events = listOf<CampaignEvent>(start(template))
        jobs.forEachIndexed { index, job ->
            // Deal something harmless, keep it, then play the job.
            events = events + offer(job) + keep(job) + play(job)

            val state = engine.fold(template, events)
            val left = choosable(template, state)
            if (index < jobs.lastIndex) {
                assertFalse(
                    "boss offered after ${index + 1} of 5 jobs: $left",
                    left == listOf("s6_caid"),
                )
                assertFalse("campaign ended early after ${index + 1} jobs", state.finished)
            } else {
                assertEquals("only the boss is left", listOf("s6_caid"), left)
            }
        }
    }

    /**
     * The campaign has to be survivable, and only a faithful deal proves it.
     *
     * The other tests here deal one environment a rotation, which is the gentle
     * case: a lone environment takes its two ticks and the job is played out
     * from under them. The app deals two, every rotation, into a pile that
     * shrinks as jobs are settled — so the last job standing collects ticks it
     * cannot dodge. Ending the campaign on the first fall made this
     * unwinnable in every one of three thousand simulated runs, with perfect
     * play, and no test noticed because no test dealt the way the app deals.
     */
    @Test
    fun `the campaign can be played through to the boss`() {
        val template = template()
        val pressureOf = template.scenarios.associate { it.id to it.pressureCounterId }

        for (seed in 0..199) {
            val random = Random(seed)
            var events = listOf<CampaignEvent>(start(template))
            var kept = setOf<String>()
            var rotations = 0

            while (true) {
                val state = engine.fold(template, events)
                assertFalse("campaign lost on seed $seed", state.campaignLost)

                val live = choosable(template, state).filter { it != template.finaleScenarioId }
                if (live.isEmpty()) {
                    assertEquals(
                        "only the boss is left on seed $seed",
                        listOf(template.finaleScenarioId),
                        choosable(template, state),
                    )
                    break
                }

                // The pool the repository deals from: places still in play,
                // minus the ones already kept, re-formed when that empties.
                val pool = live.filterNot { it in kept }.ifEmpty { live }
                val offered = if (pool.size == 1) pool else pool.shuffled(random).take(2)
                val chosen = offered.random(random)
                kept = kept + chosen

                // Play whichever job is closest to falling — the best a table
                // can do, and so the fairest test of whether it is enough.
                val next = engine.fold(template, events + offer(*offered.toTypedArray()) + keep(chosen))
                val target = live.maxBy { next.counter(pressureOf[it].orEmpty()) }
                events = events + offer(*offered.toTypedArray()) + keep(chosen) + play(target)

                assertTrue("seed $seed never resolved", ++rotations <= 12)
            }
        }
    }
    /**
     * Mary Typhoide: won once, carried, and lost for good.
     *
     * Both questions used to be put after every scenario, so a table that never
     * met her was asked twice a game about a card they do not own. Trust is
     * asked only of the job she is behind; the victory pile only while she is
     * actually in the decks.
     */
    // --- what the English rulebook (mc60, 2026) settled -------------------------

    private fun lose(id: String, vararg booleans: Pair<String, Boolean>, expert: Boolean = false) = listOf(
        CampaignEvent.ScenarioChosen(id = "choose-${tick()}", timestamp = clock, scenarioId = id),
        CampaignEvent.ScenarioCompleted(
            id = "lost-${tick()}",
            timestamp = clock,
            scenarioId = id,
            victory = false,
            answers = AnswerSet(booleans = booleans.toMap()),
        ),
        CampaignEvent.OutcomeContinued(
            id = "on-${tick()}",
            timestamp = clock,
            scenarioId = id,
            victory = false,
        ),
    )

    private fun startExpert(template: CampaignTemplate) = start(template).copy(difficulty = "expert")

    @Test
    fun `every outcome page has something to say in both languages`() {
        val template = template()
        template.scenarios.forEach { scenario ->
            listOf("victory" to scenario.onVictory, "defeat" to scenario.onDefeat).forEach { (kind, outcome) ->
                val message = outcome?.message
                assertTrue("${scenario.id} $kind has no message", message != null)
                assertTrue("${scenario.id} $kind en", message!!.resolve("en").isNotBlank())
                assertTrue("${scenario.id} $kind fr", message.resolve("fr").isNotBlank())
            }
        }
    }

    @Test
    fun `losing a job and moving on does not fail it`() {
        val template = template()
        // p.8: "Losing a scenario does not automatically cause it to fail and it
        // may be able to be attempted again." Only three marks fail a job, and
        // on Standard a defeat adds none. The job stays on the list, and its
        // environment goes on neither face.
        val state = engine.fold(
            template,
            listOf(start(template)) + offer("s2_poursuite", "s3_racket") + keep("s2_poursuite") +
                lose("s1_musee"),
        )

        assertTrue("still open", "s1_musee" in choosable(template, state))
        assertEquals(0, state.counter("pressionMusee"))
        assertFalse(state.flag("echoue", "s1_musee"))
        assertFalse(state.flag("acheve", "s1_musee"))
    }

    @Test
    fun `on Expert a defeat is one mark, and three of them fail the job`() {
        val template = template()
        val state = engine.fold(
            template,
            listOf(startExpert(template)) + offer("s2_poursuite", "s3_racket") + keep("s2_poursuite") +
                lose("s1_musee") + lose("s1_musee"),
        )
        assertEquals(2, state.counter("pressionMusee"))
        assertTrue("two marks: still open", "s1_musee" in choosable(template, state))

        val failed = engine.fold(template, listOf(startExpert(template)) +
            offer("s2_poursuite", "s3_racket") + keep("s2_poursuite") +
            lose("s1_musee") + lose("s1_musee") + lose("s1_musee"))
        assertEquals(3, failed.counter("pressionMusee"))
        assertFalse("three marks: failed", "s1_musee" in choosable(template, failed))
        assertFalse("a failed job is not the campaign", failed.campaignLost)
    }

    @Test
    fun `on Expert the boss takes a Completed environment on a defeat, or the campaign`() {
        val template = template()
        val five = listOf("s1_musee", "s2_poursuite", "s3_racket", "s4_raft", "s5_rotatives")
        val through: List<CampaignEvent> = listOf(startExpert(template)) + five.flatMap { listOf(offer(it), keep(it)) + play(it) }

        // One sacrificed: the job is now Failed, the boss is replayed.
        val flipped = engine.fold(
            template,
            through + lose("s6_caid", "flip_s3_racket" to true, expert = true),
        )
        assertFalse(flipped.flag("acheve", "s3_racket"))
        assertTrue(flipped.flag("echoue", "s3_racket"))
        assertEquals("s6_caid", flipped.currentScenarioId)
        assertFalse(flipped.campaignLost)
        assertFalse(flipped.finished)

        // Every one of the five sacrificed, then one more defeat: lost.
        var events: List<CampaignEvent> = through
        for (id in five) {
            events = events + lose("s6_caid", "flip_$id" to true, expert = true)
        }
        val stillOn = engine.fold(template, events)
        assertFalse("the fifth sacrifice is still a replay", stillOn.campaignLost)
        val lost = engine.fold(template, events + lose("s6_caid", expert = true))
        assertTrue("nothing left to give: the campaign is lost", lost.campaignLost)
        assertTrue(lost.finished)
    }

    @Test
    fun `on Standard the boss is simply replayed`() {
        val template = template()
        val five = listOf("s1_musee", "s2_poursuite", "s3_racket", "s4_raft", "s5_rotatives")
        val state = engine.fold(
            template,
            listOf(start(template)) + five.flatMap { listOf(offer(it), keep(it)) + play(it) } +
                lose("s6_caid") + lose("s6_caid"),
        )
        assertEquals("s6_caid", state.currentScenarioId)
        assertFalse(state.campaignLost)
        assertTrue("nothing was flipped", five.all { state.flag("acheve", it) })
    }

    @Test
    fun `Mary Typhoide is asked about only by tables that have met her`() {
        val template = template()
        val mary = "fne_villain_mary_typhoide"

        fun promptIds(state: CampaignState, scenarioId: String): List<String> {
            val scenario = template.scenarios.first { it.id == scenarioId }
            val context = EvaluationContext(state = state, scenarioId = scenarioId)
            return scenario.onVictory?.prompts.orEmpty()
                .filter { ConditionEvaluator.evaluate(it.condition, context) }
                .map { it.id }
        }

        fun setupLines(state: CampaignState, scenarioId: String): List<String> {
            val scenario = template.scenarios.first { it.id == scenarioId }
            val context = EvaluationContext(state = state, scenarioId = scenarioId)
            return scenario.allSetupSteps()
                .filter { ConditionEvaluator.evaluate(it.condition, context) }
                .mapNotNull { it.text.fr }
        }

        fun deal(scenarioId: String, villain: String) = CampaignEvent.SetupDrawn(
            id = "draw-${tick()}",
            timestamp = clock,
            scenarioId = scenarioId,
            drawId = "villain",
            cardCodes = listOf(villain),
        )

        fun finish(scenarioId: String, vararg answers: Pair<String, Boolean>) = listOf(
            CampaignEvent.ScenarioChosen(
                id = "choose-${tick()}", timestamp = clock, scenarioId = scenarioId,
            ),
            CampaignEvent.ScenarioCompleted(
                id = "done-${tick()}",
                timestamp = clock,
                scenarioId = scenarioId,
                victory = true,
                answers = AnswerSet(booleans = answers.toMap()),
            ),
        )

        // A job with somebody else behind it asks about neither.
        var events = listOf<CampaignEvent>(start(template)) +
            deal("s1_musee", "fne_villain_bullseye") +
            deal("s2_poursuite", mary)
        var state = engine.fold(template, events)
        assertFalse("trust asked of a job Mary is not behind",
            promptIds(state, "s1_musee").contains("confiance"))
        assertFalse("victory pile asked before she is ever met",
            promptIds(state, "s1_musee").contains("mary"))

        // Her own job asks whether she was won over, and nothing else.
        //
        // It used to ask about the victory display here too. It cannot happen:
        // her forced interruption replaces her defeat with a damage token on
        // Psyché Perturbée and resets her health, so the one time she is faced
        // as the villain is the one time she cannot end up there. Asking put a
        // question to the table that has only one possible answer.
        assertTrue(promptIds(state, "s2_poursuite").contains("confiance"))
        assertFalse(
            "asked about the victory display on the job she cannot be defeated on",
            promptIds(state, "s2_poursuite").contains("mary"),
        )

        // Won over, and left standing.
        events = events + finish("s2_poursuite", "confiance" to true)
        state = engine.fold(template, events)
        assertTrue(
            "she is not put into play as the campaign ally",
            setupLines(state, "s1_musee").any { it.contains("alliée de campagne") },
        )
        // Never asked to be won twice, but her survival now is.
        assertFalse(promptIds(state, "s1_musee").contains("confiance"))
        assertTrue(promptIds(state, "s1_musee").contains("mary"))

        // Lost for good: p.9 step 13 puts her out only while "Mary Defeated?"
        // is unchecked, and says nothing else. Nothing is asked again.
        events = events + finish("s1_musee", "mary" to true)
        state = engine.fold(template, events)
        val lines = setupLines(state, "s3_racket")
        assertFalse(
            "she is still being put into play after being lost",
            lines.any { it.contains("alliée de campagne") },
        )
        assertFalse(promptIds(state, "s3_racket").contains("mary"))
    }
}
