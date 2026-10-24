package com.xburnsx.toutiebudget.data.room.converters

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * TypeConverter pour convertir les dates String vers Long pour Room.
 * Permet de stocker les dates comme String dans Room (comme Pocketbase)
 * tout en permettant les conversions vers Date pour l'application.
 */
class DateStringConverter {
    
    // Format principal ISO 8601 avec UTC (comme PocketBase)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    // Formats de fallback pour parser les dates existantes
    private val dateFormatsFallback = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )
    
    /**
     * Convertit une String date vers Long (timestamp)
     */
    @TypeConverter
    fun fromDateString(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null
        // Essayer tous les formats
        for (format in dateFormatsFallback) {
            try {
                return format.parse(dateString)?.time
            } catch (_: Exception) {
                // Continuer avec le prochain format
            }
        }
        return null
    }
    
    /**
     * Convertit un Long (timestamp) vers String date
     */
    @TypeConverter
    fun toDateString(timestamp: Long?): String? {
        if (timestamp == null) return null
        return try {
            dateFormatter.format(Date(timestamp))
        } catch (e: Exception) {
            null
        }
    }
}
