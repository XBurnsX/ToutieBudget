/// <reference path="../pb_data/types.d.ts" />

// HG4 : journal des mouvements du budget conservé sur le serveur pour tous les appareils.
// Chaque événement garde l'ID créé sur l'appareil : le recevoir deux fois ne crée jamais de doublon.
migrate((app) => {
    try {
        app.findCollectionByNameOrId("historique_allocation");
        return; // déjà créée
    } catch (_) {
        // absente : la créer
    }

    const proprietaire = '@request.auth.id != "" && utilisateur_id = @request.auth.id';
    const collection = new Collection({
        type: "base",
        name: "historique_allocation",
        listRule: proprietaire,
        viewRule: proprietaire,
        createRule: '@request.auth.id != "" && @request.body.utilisateur_id = @request.auth.id',
        updateRule: proprietaire + ' && (@request.body.utilisateur_id:isset = false || @request.body.utilisateur_id = utilisateur_id)',
        deleteRule: proprietaire,
        fields: [
            { type: "text", name: "utilisateur_id", required: true, max: 64 },
            { type: "text", name: "compte_id", max: 64 },
            { type: "text", name: "collection_compte", max: 64 },
            { type: "text", name: "enveloppe_id", max: 64 },
            { type: "text", name: "enveloppe_nom", max: 200 },
            { type: "text", name: "type_action", max: 64 },
            { type: "text", name: "description", max: 500 },
            { type: "number", name: "montant" },
            { type: "number", name: "solde_avant" },
            { type: "number", name: "solde_apres" },
            { type: "number", name: "pret_a_placer_avant" },
            { type: "number", name: "pret_a_placer_apres" },
            { type: "text", name: "date_action", required: true, max: 32 },
            { type: "text", name: "allocation_id", max: 64 },
            { type: "text", name: "transaction_id", max: 64 },
            { type: "text", name: "virement_id", max: 64 },
            { type: "text", name: "details", max: 2000 },
            { type: "autodate", name: "created", onCreate: true, onUpdate: false },
            { type: "autodate", name: "updated", onCreate: true, onUpdate: true }
        ],
        indexes: [
            "CREATE INDEX idx_historique_allocation_utilisateur ON historique_allocation (utilisateur_id, date_action)"
        ]
    });
    app.save(collection);
}, (app) => {
    // Ne jamais supprimer automatiquement le journal pendant une rétrogradation.
});
