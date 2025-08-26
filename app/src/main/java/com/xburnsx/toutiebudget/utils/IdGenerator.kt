package com.xburnsx.toutiebudget.utils

import java.util.UUID

/**
 * Générateur d'ID uniforme à 15 caractères
 * Utilise UUID et tronque à 15 caractères pour la compatibilité Room/Pocketbase
 */
object IdGenerator {
    
    /**
     * Génère un ID unique à 15 caractères
     * Format: 15 caractères alphanumériques (0-9, a-f)
     */
    fun generateId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(15)
    }
    
    /**
     * Génère un ID unique à 15 caractères avec préfixe
     * Format: [PREFIX][15-PREFIX_LENGTH caractères]
     */
    fun generateIdWithPrefix(prefix: String): String {
        require(prefix.length < 15) { "Le préfixe doit faire moins de 15 caractères" }
        val remainingLength = 15 - prefix.length
        val randomPart = UUID.randomUUID().toString().replace("-", "").take(remainingLength)
        return prefix + randomPart
    }
    
    /**
     * Vérifie si un ID est valide (15 caractères, alphanumérique)
     */
    fun isValidId(id: String): Boolean {
        return id.length == 15 && id.all { it.isLetterOrDigit() }
    }
}
