package com.xburnsx.toutiebudget.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.Date

class SafeDateAdapter : TypeAdapter<Date?>() {
    override fun write(out: JsonWriter, value: Date?) {
        if (value == null) out.nullValue()
        else out.value(value.time)
    }
    override fun read(reader: JsonReader): Date? {
        return when (reader.peek()) {
            com.google.gson.stream.JsonToken.NULL -> {
                reader.nextNull(); null
            }
            com.google.gson.stream.JsonToken.STRING -> {
                val str = reader.nextString(); if (str.isBlank()) null else try { Date(str.toLong()) } catch (_: Exception) { null }
            }
            com.google.gson.stream.JsonToken.NUMBER -> Date(reader.nextLong())
            else -> { reader.skipValue(); null }
        }
    }
} 