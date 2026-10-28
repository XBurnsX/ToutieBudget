package com.xburnsx.toutiebudget.data.repositories.impl

/** ARA4 : date d'archivage au format des autres dates simples de l'app (yyyy-MM-dd, fuseau local). */
internal object InfosArchivage {
    fun aujourdhui(): String = java.time.LocalDate.now().toString()
}
