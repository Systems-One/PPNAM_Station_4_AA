package com.mitas.ppnam.station4aa.data.mqtt

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.mitas.ppnam.station4aa.data.mqtt.dto.ResponseEnvelope

/**
 * The Gson every login-exchange response DTO is read with. Ported from Station 2 AA's WireJson.
 *
 * A wire response reporting "nothing here" as an explicit JSON `null` (rather than omitting the
 * key) would otherwise bind straight into a non-null Kotlin `String` field by reflection, going
 * around the type system — nothing complains at parse time, until some non-null consumer receives
 * it and `Intrinsics.checkNotNullParameter` throws, arbitrarily far from the wire. Deleting JSON
 * nulls before Gson binds them makes a null-valued key indistinguishable from an absent one, so
 * every Kotlin default holds.
 *
 * Only DTOs in [ResponseEnvelope]'s package are wrapped — Gson's own adapters for strings,
 * numbers and collections are untouched, and outbound request payloads (built by hand in
 * `RequestEnvelope`, not read through this) pass through unaffected.
 */
object WireJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(NullPruningTypeAdapterFactory)
        .create()
}

private object NullPruningTypeAdapterFactory : TypeAdapterFactory {

    private val wirePackagePrefix = ResponseEnvelope::class.java.name.substringBeforeLast('.') + "."

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!type.rawType.name.startsWith(wirePackagePrefix)) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val elements = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)
            override fun read(reader: JsonReader): T = delegate.fromJsonTree(prune(elements.read(reader)))
        }.nullSafe()
    }

    private fun prune(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().also { kept ->
            for ((name, value) in element.asJsonObject.entrySet()) {
                if (!value.isJsonNull) kept.add(name, prune(value))
            }
        }
        element.isJsonArray -> JsonArray().also { kept ->
            for (item in element.asJsonArray) {
                if (!item.isJsonNull) kept.add(prune(item))
            }
        }
        else -> element
    }
}
