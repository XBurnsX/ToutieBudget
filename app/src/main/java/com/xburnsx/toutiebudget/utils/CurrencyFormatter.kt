package com.xburnsx.toutiebudget.utils

/**
 * Fonction utilitaire pour formater une chaîne de chiffres en devise canadienne.
 * Exemple: "1234" -> "12,34$"
 * 
 * Cette fonction traite les montants comme des centimes et les formate
 * en dollars canadiens avec virgule comme séparateur décimal.
 */
fun formatToCurrency(digits: String): String {
    if (digits.isEmpty()) {
        return "0,00$"
    }
    // Ajoute des zéros au début pour avoir au moins 3 chiffres pour le formatage
    val padded = digits.padStart(3, '0')
    val integerPart = padded.substring(0, padded.length - 2)
    val decimalPart = padded.substring(padded.length - 2)
    return "$integerPart,$decimalPart$"
} 