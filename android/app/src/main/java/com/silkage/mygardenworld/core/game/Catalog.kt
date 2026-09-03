package com.silkage.mygardenworld.core.game

import android.content.Context
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * Trimmed client catalog produced at build time from web/src/lib/game/catalog.json.
 * Only item display names and light flower metadata are kept.
 */
class Catalog private constructor(
    private val names: Map<Int, String>,
    val flowers: Map<Int, FlowerMeta>,
) {
    data class FlowerMeta(val id: Int, val seedId: Int, val sort: Int, val gold: Int, val experience: Int)

    fun itemName(id: Int): String = names[id] ?: if (id != 0) "#$id" else ""

    fun itemName(id: Long): String = itemName(id.toInt())

    companion object {
        val EMPTY = Catalog(emptyMap(), emptyMap())

        fun load(context: Context): Catalog = runCatching {
            context.assets.open("catalog.json").use { stream -> parse(JsonReader(InputStreamReader(stream, Charsets.UTF_8))) }
        }.getOrDefault(EMPTY)

        fun parse(reader: JsonReader): Catalog {
            val names = HashMap<Int, String>()
            val flowers = HashMap<Int, FlowerMeta>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "items" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val id = reader.nextName().toIntOrNull()
                            val name = reader.nextString()
                            if (id != null) names[id] = name
                        }
                        reader.endObject()
                    }
                    "flowers" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val id = reader.nextName().toIntOrNull()
                            var seed = 0; var sort = 0; var gold = 0; var exp = 0
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "seed_id" -> seed = reader.nextInt()
                                    "sort" -> sort = reader.nextInt()
                                    "gold" -> gold = reader.nextInt()
                                    "experience" -> exp = reader.nextInt()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (id != null) flowers[id] = FlowerMeta(id, seed, sort, gold, exp)
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return Catalog(names, flowers)
        }
    }
}
