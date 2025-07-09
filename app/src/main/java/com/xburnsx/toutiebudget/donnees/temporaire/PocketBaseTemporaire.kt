package com.xburnsx.toutiebudget.donnees.temporaire

// Classes temporaires pour remplacer PocketBase pendant la résolution des dépendances

class Pocketbase(url: String) {
    val authStore = AuthStore()

    fun collection(name: String) = Collection(name)
}

class AuthStore {
    var token: String = ""
    var model: Any? = null
    var isValid: Boolean = false

    fun onChange(callback: (token: String, model: Any?) -> Unit) {}
    fun clear() {
        token = ""
        model = null
        isValid = false
    }
}

class Collection(private val name: String) {
    suspend fun authWithPassword(email: String, password: String): AuthResult {
        // Implémentation temporaire
        return AuthResult()
    }

    suspend fun getFullList(): List<Record> {
        // Implémentation temporaire
        return emptyList()
    }

    suspend fun subscribe(callback: (action: String, record: Record) -> Unit) {
        // Implémentation temporaire
    }

    fun desabonner() {
        // Implémentation temporaire
    }
}

class AuthResult {
    val token: String = ""
    val record: Record = Record()
}

class Record {
    val id: String = ""

    fun getString(key: String): String = ""
    fun getDouble(key: String): Double = 0.0
    fun getLong(key: String): Long = 0L
    fun getBoolean(key: String): Boolean = false
    fun getInt(key: String): Int = 0
}
