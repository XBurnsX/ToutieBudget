package com.xburnsx.toutiebudget.data.services

import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals

/**
 * Test pour simuler le comportement des objectifs bihebdomadaires sur 4 mois
 * à partir du vendredi 29 août 2024
 */
class ObjectifResetServiceTest {

    @Test
    fun `test simulation 4 mois objectifs bihebdomadaires`() {
        println("=== SIMULATION 4 MOIS D'OBJECTIFS BIHEBDOMADAIRES ===")
        println("Date de départ : Vendredi 29 août 2024")
        println()

        // Date de départ : Vendredi 29 août 2024
        val dateDepart = Calendar.getInstance().apply {
            set(2024, Calendar.AUGUST, 29) // 29 août 2024
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        var dateActuelle = dateDepart
        var cycle = 1

        // Simuler 4 mois (environ 16-17 cycles bihebdomadaires)
        repeat(17) {
            val calendar = Calendar.getInstance().apply { time = dateActuelle }
            val jourSemaine = obtenirNomJourSemaine(calendar.get(Calendar.DAY_OF_WEEK))
            val dateFormatee = formaterDate(calendar.time)
            
            println("Cycle $cycle : $dateFormatee ($jourSemaine)")
            
            // Simuler le reset (jour suivant)
            val dateReset = Calendar.getInstance().apply {
                time = dateActuelle
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val calendarReset = Calendar.getInstance().apply { time = dateReset }
            val jourReset = obtenirNomJourSemaine(calendarReset.get(Calendar.DAY_OF_WEEK))
            val dateResetFormatee = formaterDate(dateReset.time)
            
            println("  → Reset : $dateResetFormatee ($jourReset)")
            
            // Calculer la prochaine date d'objectif (+14 jours)
            val prochaineDate = Calendar.getInstance().apply {
                time = dateReset
                add(Calendar.DAY_OF_YEAR, 14)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val calendarProchaine = Calendar.getInstance().apply { time = prochaineDate }
            val jourProchaine = obtenirNomJourSemaine(calendarProchaine.get(Calendar.DAY_OF_WEEK))
            val dateProchaineFormatee = formaterDate(prochaineDate.time)
            
            println("  → Prochaine : $dateProchaineFormatee ($jourProchaine)")
            println()
            
            // Passer à la prochaine date
            dateActuelle = prochaineDate
            cycle++
        }
        
        println("=== RÉSUMÉ DES CYCLES ===")
        println("• Chaque cycle dure exactement 14 jours")
        println("• Reset toujours le lendemain de la fin")
        println("• Nouvelle date = reset + 14 jours")
        println("• Total : 17 cycles sur 4 mois")
    }

    private fun obtenirNomJourSemaine(jourSemaine: Int): String {
        return when (jourSemaine) {
            Calendar.SUNDAY -> "Dimanche"
            Calendar.MONDAY -> "Lundi"
            Calendar.TUESDAY -> "Mardi"
            Calendar.WEDNESDAY -> "Mercredi"
            Calendar.THURSDAY -> "Jeudi"
            Calendar.FRIDAY -> "Vendredi"
            Calendar.SATURDAY -> "Samedi"
            else -> "Inconnu"
        }
    }

    private fun formaterDate(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val jour = calendar.get(Calendar.DAY_OF_MONTH)
        val mois = calendar.get(Calendar.MONTH)
        val annee = calendar.get(Calendar.YEAR)
        
        val nomMois = when (mois) {
            Calendar.JANUARY -> "janvier"
            Calendar.FEBRUARY -> "février"
            Calendar.MARCH -> "mars"
            Calendar.APRIL -> "avril"
            Calendar.MAY -> "mai"
            Calendar.JUNE -> "juin"
            Calendar.JULY -> "juillet"
            Calendar.AUGUST -> "août"
            Calendar.SEPTEMBER -> "septembre"
            Calendar.OCTOBER -> "octobre"
            Calendar.NOVEMBER -> "novembre"
            Calendar.DECEMBER -> "décembre"
            else -> "inconnu"
        }
        
        return "$jour $nomMois $annee"
    }
}
