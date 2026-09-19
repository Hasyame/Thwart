package com.hasyame.marvelchampions.domain.achievements

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared test vectors, docs/spec/achievements/test-vectors.json in the
 * web repository, copied here unchanged. Every `expected` block must come
 * out of [AchievementDerivation.derive] exactly: arrays in order, object
 * keys in any order, null distinct from absent. The expected values are
 * the contract; an implementation that disagrees with one is wrong, not
 * the vector.
 */
class AchievementVectorsTest {

    private val json = Json

    private val file: JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/achievements/test-vectors.json")!!.readText()).jsonObject

    @Test
    fun `the vectors are the schema and scale this build implements`() {
        assertEquals(AchievementDefinitions.SCHEMA_VERSION, file.getValue("definitionsSchemaVersion").jsonPrimitive.content.toInt())
        assertEquals(DifficultyLevel.SCALE_VERSION, file.getValue("difficultyScaleVersion").jsonPrimitive.content.toInt())
        assertTrue(file.getValue("cases").jsonArray.size >= 20)
    }

    @Test
    fun `every vector derives to its expected state, field for field`() {
        val failures = mutableListOf<String>()
        file.getValue("cases").jsonArray.forEach { case ->
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected")
            val actual = json.encodeToJsonElement(AchievementDerivation.derive(input(case.jsonObject.getValue("input").jsonObject)))
            if (actual != expected) {
                failures += "$name:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(failures.joinToString("\n\n"), 0, failures.size)
    }

    @Test
    fun `detail checklists match shared progress and ignore input order`() {
        file.getValue("cases").jsonArray.forEach { case ->
            val input = input(case.jsonObject.getValue("input").jsonObject)
            val state = AchievementDerivation.derive(input)
            input.definitions.forEach { definition ->
                AchievementDetails.targets(input, definition)?.let { targets ->
                    val progress = state.achievements.first { it.id == definition.id }.progress
                    assertEquals(definition.id, progress.target, targets.size)
                    assertEquals(definition.id, progress.current, targets.count { it.completedBy != null })
                    assertEquals(targets, AchievementDetails.targets(input.copy(facts = input.facts.reversed()), definition))
                }
            }
        }
    }

    private fun input(o: JsonObject): DeriveInput = DeriveInput(
        definitions = o.getValue("definitions").jsonArray.map { AchievementDefinitions.definition(it.jsonObject) },
        definitionsVersion = o.getValue("definitionsVersion").jsonPrimitive.content.toInt(),
        catalogue = json.decodeFromJsonElement(o.getValue("catalogue")),
        ownedPacks = o.getValue("ownedPacks").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        facts = o.getValue("facts").jsonArray.map { json.decodeFromJsonElement<PlayFact>(it) },
        runs = o.getValue("runs").jsonArray.map { json.decodeFromJsonElement<RunFact>(it) },
    )

}
