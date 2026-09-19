package com.hasyame.marvelchampions.data.db.entity

import androidx.room.TypeConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** No unknown fields: what every record starts with. */
val NO_EXTRAS: JsonObject = JsonObject(emptyMap())

/**
 * A record's JSON shape with the keys this build does not know kept beside
 * the ones it does, and written back untouched.
 *
 * The backup and the sync body are one contract shared with the web, and
 * the rule that makes a format bump safe is that a client one release
 * behind round-trips a newer file whole (docs/spec/achievements/sync.md
 * §2). So a record decodes in two halves: the keys the generated
 * serializer names go through it, everything else lands in the record's
 * `extra` object; and encoding spreads `extra` back first, then the known
 * fields over it, so a known field always wins over an extra key of the
 * same name. Known keys are never in `extra`.
 *
 * [omitWhenNull] names the optional keys the contract writes only when set,
 * so a record from before they existed exports as it was.
 */
@OptIn(ExperimentalSerializationApi::class)
class WithExtrasSerializer<T>(
    private val generated: KSerializer<T>,
    private val extrasOf: (T) -> JsonObject,
    private val withExtras: (T, JsonObject) -> T,
    private val omitWhenNull: Set<String> = emptySet(),
) : KSerializer<T> {

    override val descriptor: SerialDescriptor get() = generated.descriptor

    private val known: Set<String> = generated.descriptor.elementNames.toSet()

    override fun serialize(encoder: Encoder, value: T) {
        val out = encoder as? JsonEncoder ?: error("records are JSON")
        val fields = out.json.encodeToJsonElement(generated, value).jsonObject
        val merged = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        extrasOf(value).forEach { (key, element) ->
            if (key !in known) {
                merged[key] = element
            }
        }
        fields.forEach { (key, element) ->
            if (!(key in omitWhenNull && element is JsonNull)) {
                merged[key] = element
            }
        }
        out.encodeJsonElement(JsonObject(merged))
    }

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder ?: error("records are JSON")
        val whole = input.decodeJsonElement().jsonObject
        val fields = whole.filterKeys { it in known }
        val extras = whole.filterKeys { it !in known }
        val value = input.json.decodeFromJsonElement(generated, JsonObject(fields))
        return if (extras.isEmpty()) value else withExtras(value, JsonObject(extras))
    }
}

/** The unknown-field object as a text column. */
class ExtrasConverters {

    @TypeConverter
    fun toJson(extras: JsonObject): String = if (extras.isEmpty()) "{}" else extras.toString()

    @TypeConverter
    fun fromJson(value: String): JsonObject =
        if (value.isBlank() || value == "{}") {
            NO_EXTRAS
        } else {
            runCatching { Json.parseToJsonElement(value).jsonObject }.getOrDefault(NO_EXTRAS)
        }
}
