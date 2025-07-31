package com.xburnsx.toutiebudget.utils

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Utilitaire pour le formatage cohérent des montants d'argent
 * Assure que les montants sont toujours affichés avec 2 décimales
 */
object MoneyFormatter {
    
    /**
     * Formate un montant en devise canadienne avec toujours 2 décimales
     * Exemple: 10.7 -> "10,70 $" au lieu de "10,7 $"
     */
    fun formatAmount(amount: Double): String {
        // Si le montant est très proche de zéro, l'afficher comme 0,00
        val montantAFormater = if (abs(amount) < 0.001) 0.0 else amount
        
        // Utiliser NumberFormat avec configuration spécifique pour toujours avoir 2 décimales
        val formatter = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        
        return formatter.format(montantAFormater)
    }
    
    /**
     * Formate un montant en centimes vers la devise canadienne
     * Exemple: 1070 -> "10,70 $" (1070 centimes = 10.70$)
     */
    fun formatAmountFromCents(cents: Long): String {
        val amount = cents / 100.0
        return formatAmount(amount)
    }
    
    /**
     * Convertit un montant en dollars vers des centimes
     * Exemple: 10.70 -> 1070
     */
    fun amountToCents(amount: Double): Long {
        return (amount * 100).toLong()
    }
    
    /**
     * Convertit des centimes vers un montant en dollars
     * Exemple: 1070 -> 10.70
     */
    fun centsToAmount(cents: Long): Double {
        return cents / 100.0
    }
    
    /**
     * Valide et nettoie une entrée de montant d'argent
     * Assure que le montant est valide et arrondi correctement
     */
    fun validateAndCleanAmount(input: String): Double? {
        return try {
            // Nettoyer l'entrée (enlever espaces, symboles de devise, etc.)
            val cleaned = input.replace(Regex("[^0-9.-]"), "")
            val amount = cleaned.toDouble()
            
            // Arrondir à 2 décimales pour éviter les erreurs de précision
            kotlin.math.round(amount * 100) / 100.0
        } catch (e: NumberFormatException) {
            null
        }
    }
} 