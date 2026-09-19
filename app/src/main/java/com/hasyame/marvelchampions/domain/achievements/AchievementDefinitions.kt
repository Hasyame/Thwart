package com.hasyame.marvelchampions.domain.achievements

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the definitions file, `assets/achievements.json`: a snapshot of the
 * web repository's `web/public/achievements.json`, copied at release and
 * never fetched at runtime.
 *
 * Read by hand rather than through a generated serializer so the refusals
 * are the specification's: a `schemaVersion` this build does not
 * understand, a difficulty scale other than the one implemented, a
 * predicate kind it does not know. Any `definitionsVersion` loads.
 */
object AchievementDefinitions {

    /** The shape of a definition this build understands. */
    const val SCHEMA_VERSION = 2

    class Refused(message: String) : IllegalArgumentException(message)

    fun parse(text: String): DefinitionsFile = parse(Json.parseToJsonElement(text).jsonObject)

    fun parse(root: JsonObject): DefinitionsFile {
        val schema = root.int("schemaVersion")
        if (schema > SCHEMA_VERSION) {
            throw Refused("achievements schema $schema is newer than $SCHEMA_VERSION")
        }
        val scale = root.int("difficultyScaleVersion")
        if (scale != DifficultyLevel.SCALE_VERSION) {
            throw Refused("difficulty scale $scale is not the one implemented, ${DifficultyLevel.SCALE_VERSION}")
        }
        val achievements = root.array("achievements").map { definition(it.jsonObject) }
        val ids = achievements.map { it.id }
        require(ids.size == ids.toSet().size) { "an achievement id repeats" }
        return DefinitionsFile(
            schemaVersion = schema,
            definitionsVersion = root.int("definitionsVersion"),
            difficultyScaleVersion = scale,
            achievements = achievements,
        )
    }

    fun definition(o: JsonObject): AchievementDefinition {
        val tiers = o["tiers"]?.jsonArray?.map { tier ->
            Tier(tier = enumOf<TierName>(tier.jsonObject.string("tier")), n = tier.jsonObject.int("n"))
        }.orEmpty()
        tiers.zipWithNext().forEach { (a, b) -> require(a.n < b.n) { "tiers of ${o["id"]} are not ascending" } }
        return AchievementDefinition(
            id = o.string("id").also { require(it.matches(Regex("[a-z0-9_]+"))) { "not a slug: $it" } },
            category = enumOf(o.string("category")),
            scope = enumOf(o.string("scope")),
            hidden = o["hidden"]?.jsonPrimitive?.booleanOrNull ?: false,
            tiers = tiers,
            predicate = predicate(o["predicate"]?.jsonObject ?: throw Refused("no predicate on ${o["id"]}")),
        )
    }

    fun predicate(p: JsonObject): Predicate = when (val kind = p.string("kind")) {
        "scenarios_won" -> Predicate.ScenariosWon(p.string("pack"), p.level("minDifficulty"))
        "heroes_won" -> Predicate.HeroesWon(p.string("pack"), p.level("minDifficulty"))
        "aspects_won" -> Predicate.AspectsWon(p["scenario"]?.jsonPrimitive?.contentOrNull, p.level("minDifficulty"))
        "first_win" -> Predicate.FirstWin(p.level("minDifficulty") ?: DifficultyLevel.UNKNOWN)
        "count" -> Predicate.Count(enumOf(p.string("what")))
        "table_win" -> Predicate.TableWin(
            players = p.int("players").also { require(it in 1..4) { "players $it" } },
            distinctAspects = p["distinctAspects"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        "campaign" -> Predicate.Campaign(
            noDefeat = p["noDefeat"]?.jsonPrimitive?.booleanOrNull ?: false,
            minDifficulty = p.level("minDifficulty"),
        )
        "mode_win" -> Predicate.ModeWin(
            p.string("mode").also { require(it in PLAY_MODES) { "mode $it" } },
            if ("n" in p) p.int("n").also { require(it > 0) } else 1,
        )
        "loss_count" -> Predicate.LossCount(p.int("n").also { require(it > 0) })
        else -> throw Refused("unknown predicate kind $kind")
    }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: throw Refused("$key is not a number")

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: throw Refused("$key is missing")

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: throw Refused("$key is not a list")

    private fun JsonObject.level(key: String): DifficultyLevel? =
        this[key]?.jsonPrimitive?.contentOrNull?.let { enumOf<DifficultyLevel>(it) }

    /** By the `@SerialName` spelling, which is the file's lowercase one. */
    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw Refused("unknown ${E::class.simpleName} $name")

    /** For the tests: a raw element to a file, the same way. */
    fun parse(element: JsonElement): DefinitionsFile = parse(element.jsonObject)
}
