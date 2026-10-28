/// <reference path="../pb_data/types.d.ts" />

// ARA4 : date (yyyy-MM-dd) et raison facultative de l'archivage des comptes et enveloppes.
// Ajout seulement : aucun champ existant n'est modifié ou supprimé.
migrate((app) => {
    const collections = [
        "comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement", "enveloppes"
    ];
    for (const nom of collections) {
        let collection;
        try {
            collection = app.findCollectionByNameOrId(nom);
        } catch (_) {
            continue; // collection absente sur ce serveur
        }
        if (!collection.fields.getByName("archive_le")) {
            collection.fields.add(new TextField({ name: "archive_le", required: false, max: 10 }));
        }
        if (!collection.fields.getByName("raison_archivage")) {
            collection.fields.add(new TextField({ name: "raison_archivage", required: false, max: 500 }));
        }
        app.save(collection);
    }

    // GN3 : montant reporté au mois suivant par le rollover (solde réel de fin de mois)
    try {
        const allocations = app.findCollectionByNameOrId("allocations_mensuelles");
        if (!allocations.fields.getByName("montant_reporte")) {
            allocations.fields.add(new NumberField({ name: "montant_reporte", required: false }));
            app.save(allocations);
        }
    } catch (_) {
        // collection absente sur ce serveur
    }
}, (app) => {
    // Ne pas effacer l'historique d'archivage pendant une rétrogradation.
});
