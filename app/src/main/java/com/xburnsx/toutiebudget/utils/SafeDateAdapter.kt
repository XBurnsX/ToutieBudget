package com.xburnsx.toutiebudget.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SafeDateAdapter : TypeAdapter<Date?>() {

    // Formats de date à essayer pour le parsing
    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )

    override fun write(out: JsonWriter, value: Date?) {
        if (value == null) {
            out.nullValue()
        } else {
            // Écrire au format ISO 8601
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            out.value(formatter.format(value))
        }
    }

    override fun read(reader: JsonReader): Date? {
        return when (reader.peek()) {
            com.google.gson.stream.JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            com.google.gson.stream.JsonToken.STRING -> {
                val dateString = reader.nextString()
                if (dateString.isBlank()) {
                    null
                } else {
                    parseDate(dateString)
                }
            }
            com.google.gson.stream.JsonToken.NUMBER -> {
                // Timestamp en millisecondes
                try {
                    Date(reader.nextLong())
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun parseDate(dateString: String): Date? {
        // Essayer de parser avec différents formats
        for (format in dateFormats) {
            try {
                return format.parse(dateString)
            } catch (e: Exception) {
                // Continuer avec le prochain format
            }
        }

        // Si aucun format ne fonctionne, essayer comme timestamp
        try {
            val timestamp = dateString.toLong()
            return Date(timestamp)
        } catch (e: Exception) {
            // Dernier recours : retourner null
            return null
        }
    }
}
