package com.xburnsx.toutiebudget

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Classe Application de base.
 * L'annotation @HiltAndroidApp est cruciale. Elle initialise Hilt (notre système
 * d'injection de dépendances) et lui permet de fonctionner dans toute l'application.
 * Ce fichier est déclaré une seule fois dans le Manifeste.
 */
@HiltAndroidApp
class ToutieBudgetApp : Application()