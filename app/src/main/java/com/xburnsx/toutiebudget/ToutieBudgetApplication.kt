package com.xburnsx.toutiebudget

import android.app.Application
import com.xburnsx.toutiebudget.di.AppModule

/**
 * Classe Application personnalisée pour ToutieBudget
 * Responsable de l'initialisation de la base de données Room
 */
class ToutieBudgetApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialiser la base de données Room
        AppModule.initializeDatabase(this)
    }
}
