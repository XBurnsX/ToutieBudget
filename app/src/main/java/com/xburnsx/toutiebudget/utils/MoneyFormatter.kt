package com.xburnsx.toutiebudget.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Utilitaire pour le formatage cohérent des montants d'argent
 * Assure que les montants sont toujours affichés avec 2 décimales et une précision optimale
 */
object MoneyFormatter {
    
    /**
     * 🎯 FONCTION UTILITAIRE GLOBALE : Normalise un montant avec précision optimale
     * Utilise BigDecimal pour éliminer complètement les erreurs de précision
     * Arrondit à 2 décimales et applique une tolérance pour éviter les erreurs de précision
     */
    fun normalizeAmount(amount: Double): Double {
        // Utiliser BigDecimal pour une précision parfaite
        val bigDecimal = BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
        
        val result = bigDecimal.toDouble()
        
        // Éliminer complètement les valeurs très proches de zéro (comme 0.005)
        return if (abs(result) < 0.01) 0.0 else result
    }
    
    /**
     * 🎯 FONCTION UTILITAIRE GLOBALE : Force l'arrondi d'un montant à 2 décimales
     * Utilise BigDecimal pour garantir une précision parfaite
     */
    fun roundAmount(amount: Double): Double {
        return BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
    
    /**
     * 🎯 FONCTION UTILITAIRE GLOBALE : Compare deux montants avec précision
     * Utilise une tolérance pour éviter les erreurs de précision des nombres à virgule flottante
     */
    fun isAmountZero(amount: Double): Boolean {
        return abs(normalizeAmount(amount)) < 0.01
    }
    
    /**
     * 🎯 FONCTION UTILITAIRE GLOBALE : Compare deux montants avec précision
     * Utilise une tolérance pour éviter les erreurs de précision des nombres à virgule flottante
     */
    fun areAmountsEqual(amount1: Double, amount2: Double): Boolean {
        val normalized1 = normalizeAmount(amount1)
        val normalized2 = normalizeAmount(amount2)
        return abs(normalized1 - normalized2) < 0.01
    }
    
    /**
     * Formate un montant en devise canadienne avec toujours 2 décimales
     * Exemple: 10.7 -> "10,70 $" au lieu de "10,7 $"
     */
    fun formatAmount(amount: Double): String {
        // 🎯 UTILISER LA NORMALISATION GLOBALE POUR UNE PRÉCISION COHÉRENTE
        val montantNormalise = normalizeAmount(amount)
        
        // Utiliser NumberFormat avec configuration spécifique pour toujours avoir 2 décimales
        val formatter = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        
        return formatter.format(montantNormalise)
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
        val montantNormalise = normalizeAmount(amount)
        // Utiliser BigDecimal pour éviter les erreurs de précision
        return BigDecimal.valueOf(montantNormalise)
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
    
    /**
     * Convertit des centimes vers un montant en dollars
     * Exemple: 1070 -> 10.70
     */
    fun centsToAmount(cents: Long): Double {
        // Utiliser BigDecimal pour éviter les erreurs de précision
        val amount = BigDecimal.valueOf(cents)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .toDouble()
        return normalizeAmount(amount)
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
            
            // 🎯 UTILISER LA NORMALISATION GLOBALE POUR UNE PRÉCISION COHÉRENTE
            normalizeAmount(amount)
        } catch (e: NumberFormatException) {
            null
        }
    }
} 