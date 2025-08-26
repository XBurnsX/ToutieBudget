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
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * Convertit une String date vers Long (timestamp)
     */
    @TypeConverter
    fun fromDateString(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null
        return try {
            dateFormatter.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
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
