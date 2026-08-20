package com.hasyame.marvelchampions.domain.campaign.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A campaign, entirely as data.
 *
 * Nothing in this file is specific to any published campaign. Adding one is a
 * JSON file; if you find yourself editing Kotlin to support a campaign, the
 * schema is missing something and that is the thing to fix.
 *
 * Templates hold mechanics only — codes, counters, conditions and effects, plus
 * short labels written for this app — so they ship in `assets/campaigns/` and a
 * campaign is playable without importing anything. They reproduce no rules text
 * and no campaign book text; `BundledCampaignsTest` holds that line with a
 * length cap on blurbs and setup steps. Templates the app does not bundle can
 * still be imported from device storage. See the Legal section of the README.
 */
@Serializable
data class CampaignTemplate(
    val id: String,
    val schemaVersion: Int,
    val name: LocalizedText,
    /** MarvelCDB pack code, so a campaign is only offered if the box is owned. */
    val packCode: String? = null,
    val difficulties: List<String> = listOf("standard", "expert"),
    val counters: List<CounterDefinition> = emptyList(),
    val flagSets: List<FlagSetDefinition> = emptyList(),
    val cardLists: List<CardListDefinition> = emptyList(),
    val market: MarketDefinition? = null,
    /**
     * Setup steps written once and included by the scenarios that share them.
     *
     * Campaigns repeat themselves: Age of Apocalypse sets up the same side
     * mission five times over. Spelling it out per scenario is not just longer,
     * it lets the five copies drift apart, and a campaign whose scenario 3
     * quietly disagrees with its scenario 2 is worse than one that is verbose.
     *
     * One level deep on purpose — a fragment cannot include another. Nested
     * includes buy nothing here and make a template hard to read in the order
     * it is played.
     */
    val setupFragments: Map<String, List<SetupStep>> = emptyMap(),
    val scenarios: List<ScenarioTemplate> = emptyList(),
    /** Scenario id the campaign starts on. Defaults to the first. */
    val startScenarioId: String? = null,
    /**
     * Played only when nothing else is left, and never offered as a choice.
     *
     * Fear No Evil keeps Le Caïd back for the end however the rest is ordered.
     */
    val finaleScenarioId: String? = null,
    /** True when the campaign asks which scenario to play first. */
    val chooseFirstScenario: Boolean = false,

    /**
     * A draw run before every choice, campaign-scoped rather than tied to a
     * scenario's setup.
     *
     * Fear No Evil's villains push the places the heroes are not: two
     * environments are drawn before each game and their scenarios advance,
     * whether or not anybody plays them. `from` names the scenario ids, `count`
     * is two, and `counts` maps each to the pressure counter it feeds. The app
     * draws it — this is the campaign automating the thing the rules leave to a
     * shuffle, not a question put to the table.
     */
    @SerialName("environmentDraw") val environmentDraw: DrawDefinition? = null,

    /**
     * Names for cards the card database does not carry, keyed by the same id a
     * draw uses.
     *
     * Fear No Evil's encounter side is not on MarvelCDB, so a drawn environment
     * or villain would otherwise show its raw id. These are French — they come
     * off the box, and there is no English printing here to copy — and the
     * English slot carries the French name too rather than a guess. The card
     * database wins wherever it has an entry, so this quietly stops mattering
     * the day the cards are published.
     */
    @SerialName("localCardNames") val localCardNames: Map<String, LocalizedText> = emptyMap(),

    /**
     * Villains dealt one per scenario when the campaign starts, and kept quiet
     * until that scenario is set up.
     *
     * Fear No Evil fixes which subordinate is behind which job before the first
     * game, and the players find out only when they get there. Dealing it at
     * setup time instead would let a table reroll it by backing out.
     */
    @SerialName("villainPool") val villainPool: List<String> = emptyList(),

    /**
     * True when a scenario reaching its [ScenarioTemplate.failedWhen] ends the
     * whole campaign in defeat.
     *
     * No bundled campaign sets this. A failed scenario is a setback everywhere,
     * including Fear No Evil, where the fallen job is simply out and the last
     * villain is harder for it — ending the run on the first fall makes that
     * campaign unwinnable, since ticks land faster than jobs can be settled.
     */
    @SerialName("losesWhenScenarioFails") val losesWhenScenarioFails: Boolean = false,

    /**
     * A short note shown before the campaign is started.
     *
     * For anything a table should know up front that is not a setup step —
     * Fear No Evil says its text is French-only for now. Written for this app,
     * like every other string in a template; nothing is quoted from a book.
     */
    @SerialName("notice") val notice: LocalizedText? = null,

    /**
     * True while the campaign is still being built.
     *
     * Marked in the chooser so a release can go out with a campaign half done
     * without anybody starting it expecting the whole thing.
     */
    @SerialName("wip") val wip: Boolean = false,
) {

    /**
     * The same campaign with every [setupFragments] include spelled out.
     *
     * Everything downstream — the engine, the validator, the briefing screen —
     * works on expanded scenarios, so nothing else in the app has to know that
     * fragments exist. An include naming a fragment that does not exist is left
     * in place for [TemplateValidator] to report rather than silently dropped.
     */
    fun expanded(): CampaignTemplate = copy(
        scenarios = scenarios.map { scenario ->
            fun expand(steps: List<SetupStep>) = steps.flatMap { step ->
                step.include?.let { setupFragments[it] } ?: listOf(step)
            }
            scenario.copy(
                preSetup = expand(scenario.preSetup),
                campaignSetup = expand(scenario.campaignSetup),
                information = expand(scenario.information),
            )
        },
    )
}

/**
 * Every setup list a scenario has, paired with the name it goes by in the JSON.
 *
 * Anything that checks setup steps — the validator, the tests that keep rules
 * text out of the templates — must see all three, and a list added later is a
 * list those checks silently stop covering unless they read it from here.
 */
/**
 * The campaign's name as the chooser shows it, flagged while it is unfinished.
 *
 * Appended rather than written into the name so it disappears by flipping one
 * field, and so no translation has to carry it.
 */
fun CampaignTemplate.chooserName(locale: String): String =
    name.resolve(locale) + if (wip) " (WIP)" else ""

fun ScenarioTemplate.setupSections(): List<Pair<String, List<SetupStep>>> = listOf(
    "preSetup" to preSetup,
    "campaignSetup" to campaignSetup,
    "information" to information,
)

/** Every setup step in a scenario, in the order the briefing shows them. */
fun ScenarioTemplate.allSetupSteps(): List<SetupStep> = setupSections().flatMap { it.second }

/**
 * Content is French first. English can be added later, and a missing string
 * falls back rather than showing a blank.
 */
@Serializable
data class LocalizedText(
    val fr: String? = null,
    val en: String? = null,
) {
    fun resolve(preferred: String): String =
        when (preferred) {
            "en" -> en ?: fr
            else -> fr ?: en
        } ?: ""
}

enum class CounterScope { CAMPAIGN, HERO }

@Serializable
data class CounterDefinition(
    val id: String,
    /** `campaign` or `hero`. Credits belong to each player, not to the group. */
    val scope: String = "campaign",
    val initial: Int = 0,
    val min: Int? = 0,
    val max: Int? = null,
    /**
     * Caps the counter at a value read from the card database rather than
     * stored here. Only `heroCard.health` is understood.
     */
    val maxFrom: String? = null,
    val activeWhen: Condition? = null,
) {
    val counterScope: CounterScope
        get() = if (scope.equals("hero", ignoreCase = true)) CounterScope.HERO else CounterScope.CAMPAIGN
}

@Serializable
data class FlagSetDefinition(
    val id: String,
    /** `perScenario` means one flag per scenario, which conditions can count. */
    val scope: String = "campaign",
)

@Serializable
data class CardListDefinition(
    val id: String,
    /** `campaign`, `hero` or `perScenario`. */
    val scope: String = "campaign",
)

@Serializable
data class MarketDefinition(
    /** Counter spent when buying. Per hero. */
    val counterId: String = "credits",
    val entries: List<MarketEntry> = emptyList(),
)

@Serializable
data class MarketEntry(
    val cardCode: String,
    val cost: Int,
    /** Card list the purchase is added to. */
    val cardListId: String = "purchases",
)

@Serializable
data class ScenarioTemplate(
    val id: String,
    val name: LocalizedText? = null,
    val flavour: LocalizedText? = null,
    /**
     * Wording for the two buttons that end a scenario, which reads better named
     * after the villain than as a generic Victory/Defeat pair.
     */
    val victoryLabel: LocalizedText? = null,
    val defeatLabel: LocalizedText? = null,
    val baseSetup: BaseSetup? = null,

    /**
     * What goes on the table before the setup proper: which decks to find, and
     * anything dealt for this scenario.
     *
     * [baseSetup] covers the same ground for campaigns whose cards the database
     * carries, as card chips. This is for the ones it does not — Fear No Evil
     * names its decks in text because none of them exist on MarvelCDB.
     */
    val preSetup: List<SetupStep> = emptyList(),
    val campaignSetup: List<SetupStep> = emptyList(),

    /**
     * Notes worth reading once the table is laid out, and no part of laying it
     * out — how the villain behaves, what to watch for.
     */
    val information: List<SetupStep> = emptyList(),
    val onVictory: Outcome? = null,
    val onDefeat: Outcome? = null,
    /**
     * When this scenario is lost without ever having been played.
     *
     * Fear No Evil pushes the scenarios nobody visits: three pushes and that
     * place is gone, whether or not the heroes ever went there. It stops being
     * offered, and its environment stays on the table as a penalty.
     *
     * Null everywhere else, where a scenario is only spent by playing it.
     */
    @SerialName("failedWhen") val failedWhen: Condition? = null,

    /**
     * The counter that measures how close this place is to falling, shown on
     * the choice screen so the table can weigh where to go.
     *
     * Null for campaigns that do not push their scenarios; the choice screen
     * shows nothing for those.
     */
    @SerialName("pressureCounterId") val pressureCounterId: String? = null,

    /**
     * Bespoke mechanics that the declarative schema genuinely cannot express,
     * resolved against a registry of Kotlin handlers. A last resort: if the same
     * shape appears twice it belongs in the schema instead.
     */
    val handlerId: String? = null,
)

@Serializable
data class BaseSetup(
    /** Villain stages per difficulty, e.g. `standard` to `["drang_1","drang_2"]`. */
    val villainDeck: Map<String, List<String>> = emptyMap(),
    /**
     * Draw id naming which villain is faced, when the scenario does not fix one.
     *
     * Fear No Evil pairs a scenario with whichever subordinate has not been
     * fought yet, and each brings its own stages — so the deck cannot be written
     * into the scenario the way every other campaign writes it.
     */
    val villainDeckFromDraw: String? = null,
    /**
     * Stages per drawn villain: card code to difficulty to stages.
     *
     * Consulted only when [villainDeckFromDraw] names a draw that has come up.
     */
    val villainDecks: Map<String, Map<String, List<String>>> = emptyMap(),
    val mainScheme: List<String> = emptyList(),
    val encounterSets: List<String> = emptyList(),
    val modularSets: List<String> = emptyList(),
)

/**
 * One instruction of the campaign-specific setup.
 *
 * With [action] set it is a button rather than a bullet: something the player
 * may choose to do that changes state, such as spending a credit to heal.
 */
@Serializable
data class SetupStep(
    /** Empty only on an [include], which is replaced before anything reads it. */
    val text: LocalizedText = LocalizedText(),
    @SerialName("when") val condition: Condition? = null,
    val action: SetupAction? = null,
    /**
     * Cards this step refers to, by MarvelCDB code.
     *
     * Structured rather than written into [text] so the UI can show the card's
     * real name in the reader's language and let them open it. A code in the
     * prose would be unreadable and would not translate.
     */
    val cards: List<String> = emptyList(),

    /**
     * Shows a counter's current value alongside the step, for instructions that
     * depend on something recorded earlier in the campaign.
     */
    val showCounter: String? = null,

    /** Shows what was recorded into a card list in an earlier scenario. */
    val showCardList: String? = null,

    /** Names the heroes whose value for this counter is above zero. */
    val showHeroesWith: String? = null,

    /**
     * Replaces this step with the named entry of
     * [CampaignTemplate.setupFragments]. Every other field is ignored.
     */
    val include: String? = null,

    /**
     * A card the app draws at random instead of the player.
     *
     * The draw is made once and recorded as an event, so it survives leaving
     * the screen and cannot change while the setup is being read.
     */
    val draw: DrawDefinition? = null,
)

/**
 * A random pick the app makes on the players' behalf.
 *
 * Campaigns that say "randomly select an available X" mean available *across
 * the campaign*: something already used is out of the pool. Doing that by hand
 * means remembering several scenarios back and re-reading the log every time,
 * which is exactly the bookkeeping the log exists to end.
 */
@Serializable
data class DrawDefinition(
    /** Names the draw, so effects can strike whatever came up. */
    val id: String,
    /** The candidates, by MarvelCDB code. */
    val from: List<String> = emptyList(),
    /**
     * Card list holding what is already spent; those are removed from the pool.
     *
     * When everything has been used the pool refills, because a scenario that
     * requires a card must still get one.
     */
    val excluding: String? = null,
    /**
     * Counters to raise when a card comes up, as card code to counter id.
     *
     * Fear No Evil sets threat from how often an environment has been drawn, so
     * the count has to be kept as the campaign runs rather than worked out from
     * the log afterwards.
     */
    val counts: Map<String, String> = emptyMap(),
    /**
     * How many candidates to offer when the players choose rather than the app.
     *
     * Zero means the draw decides, which is every other campaign.
     *
     * This used to claim Fear No Evil "keeps the one the table picks". It does
     * not, and the mistake cost a rewrite: both drawn environments push their
     * scenarios along, and the table is then free to play *any* scenario not
     * yet finished or failed — including one nobody drew. The villains hit two
     * places at once; the heroes go wherever they like, and the place they
     * ignore is the one that falls.
     */
    val offer: Int = 0,
    /**
     * How many to draw, in order. More than one is for a setup that arranges
     * several cards, such as villains laid out in a row.
     */
    val count: Int = 1,
)

@Serializable
data class SetupAction(
    val id: String,
    val label: LocalizedText,
    /** Only offered when this holds. */
    val enabledWhen: Condition? = null,
    /** Per-hero cost paid from a counter. */
    val cost: ActionCost? = null,
    val effects: List<Effect> = emptyList(),
    /** True when each hero may take it independently. */
    val perHero: Boolean = false,
    /** True when it may be taken more than once. */
    val repeatable: Boolean = false,
)

@Serializable
data class ActionCost(
    val counterId: String,
    val amount: Int,
)

@Serializable
data class Outcome(
    /**
     * What this scenario's ending means, in the campaign's own terms.
     *
     * Placeholders are resolved like any other campaign text, so a line can
     * name the villain that was drawn and the card the scenario turns over.
     * Without it every scenario ends on the same generic congratulation and
     * the campaign log advances invisibly.
     */
    val message: LocalizedText? = null,
    val prompts: List<Prompt> = emptyList(),
    val effects: List<Effect> = emptyList(),
    /**
     * Applied when the players move on from this outcome, not when it is filed.
     *
     * The difference matters wherever a result can be reconsidered. Fear No
     * Evil lets a lost job be attempted again, so losing settles nothing; it is
     * choosing to continue that turns the environment over and takes the job
     * out of the campaign.
     */
    val onContinue: List<Effect> = emptyList(),
    /** Guarded and evaluated in order; the first match wins. */
    val next: List<NextStep> = emptyList(),
)

@Serializable
data class NextStep(
    val goto: String? = null,
    @SerialName("when") val condition: Condition? = null,
    /** Ends the campaign rather than moving on. */
    val end: Boolean = false,
    /**
     * Hands the choice of the next scenario to the players.
     *
     * Fear No Evil is played in whatever order the table likes, so the campaign
     * cannot name what comes next — only that it is time to ask.
     */
    val choose: Boolean = false,
)

enum class PromptType {
    NUMBER,
    BOOLEAN,
    PER_HERO_NUMBER,
    PER_HERO_BOOLEAN,

    /**
     * Each hero picks from a known set of cards, one answer per hero.
     *
     * Distinct from [CARD_SELECT], which records a single set for the table.
     * When a campaign says "each player chooses one", two players may well pick
     * the same card, and a shared set cannot hold it twice — nor can it say who
     * took what.
     */
    PER_HERO_CARD_SELECT,
    CARD_LIST,

    /**
     * Tick which of a known set of cards applies.
     *
     * Records card codes rather than typed titles, so a later scenario can act
     * on the answer instead of only showing it back.
     */
    CARD_SELECT,

    /**
     * Tick cards out of the decks actually being played.
     *
     * The set cannot come from the template, because it is whatever the players
     * built, so the prompt is filled from the run's decks at the moment it is
     * asked. Codes are recorded rather than typed titles, which is what lets a
     * later scenario say "remove these from the game" and name them.
     */
    DECK_CARD_SELECT,
    CHOICE,
    UNKNOWN,
}

@Serializable
data class Prompt(
    val id: String,
    val type: String,
    val label: LocalizedText? = null,
    @SerialName("when") val condition: Condition? = null,
    val min: Int? = null,
    val max: Int? = null,
    /** For `choice`. */
    val options: List<PromptOption> = emptyList(),
    /** For `cardSelect`: the cards that may be ticked, by MarvelCDB code. */
    val cards: List<String> = emptyList(),
) {
    val promptType: PromptType
        get() = when (type.lowercase().replace("_", "")) {
            "number" -> PromptType.NUMBER
            "boolean" -> PromptType.BOOLEAN
            "perheronumber" -> PromptType.PER_HERO_NUMBER
            "perheroboolean" -> PromptType.PER_HERO_BOOLEAN
            "perherocardselect" -> PromptType.PER_HERO_CARD_SELECT
            "cardlist" -> PromptType.CARD_LIST
            "cardselect" -> PromptType.CARD_SELECT
            "deckcardselect" -> PromptType.DECK_CARD_SELECT
            "choice" -> PromptType.CHOICE
            else -> PromptType.UNKNOWN
        }

    val isPerHero: Boolean
        get() = promptType == PromptType.PER_HERO_NUMBER ||
            promptType == PromptType.PER_HERO_BOOLEAN ||
            promptType == PromptType.PER_HERO_CARD_SELECT
}

@Serializable
data class PromptOption(
    val id: String,
    val label: LocalizedText? = null,
)

/**
 * The villain stages to show, whether the scenario fixed them or drew them.
 *
 * Falls back to the written deck when nothing has been drawn yet, so a briefing
 * rendered before the draw lands still shows something rather than blanking.
 */
fun BaseSetup.villainStages(difficulty: String, drawnVillain: String?): List<String> {
    val fromDraw = villainDeckFromDraw
        ?.let { drawnVillain }
        ?.let { villainDecks[it] }
        ?.get(difficulty)
    return fromDraw?.takeIf { it.isNotEmpty() } ?: villainDeck[difficulty].orEmpty()
}
