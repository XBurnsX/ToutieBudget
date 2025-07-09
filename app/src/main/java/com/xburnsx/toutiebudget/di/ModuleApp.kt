package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.donnees.depot.DepotAuthentificationImpl
import com.xburnsx.toutiebudget.donnees.depot.DepotCompteImpl
import com.xburnsx.toutiebudget.donnees.depot.DepotEnveloppeImpl
import com.xburnsx.toutiebudget.domaine.depot.DepotAuthentification
import com.xburnsx.toutiebudget.domaine.depot.DepotCompte
import com.xburnsx.toutiebudget.domaine.depot.DepotEnveloppe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.xburnsx.toutiebudget.donnees.temporaire.Pocketbase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuleApp {

    @Provides
    @Singleton
    fun fournirPocketBase(): Pocketbase {
        return Pocketbase(
            url = "http://toutiebudget.duckdns.org:8090"
        )
    }

    @Provides
    @Singleton
    fun fournirDepotAuthentification(pb: Pocketbase): DepotAuthentification {
        return DepotAuthentificationImpl(pb)
    }

    @Provides
    @Singleton
    fun fournirDepotCompte(pb: Pocketbase): DepotCompte {
        return DepotCompteImpl(pb)
    }

    @Provides
    @Singleton
    fun fournirDepotEnveloppe(pb: Pocketbase): DepotEnveloppe {
        return DepotEnveloppeImpl(pb)
    }
}
