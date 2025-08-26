package com.xburnsx.toutiebudget.data.room.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Convertisseur de type pour les dates dans Room.
 * Permet de stocker et récupérer les dates dans la base de données SQLite.
 */
class DateConverter {
    
    /**
     * Convertit un timestamp Long en Date pour la base de données
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    /**
     * Convertit une Date en timestamp Long pour la base de données
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
