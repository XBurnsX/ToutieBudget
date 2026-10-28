/// <reference path="../pb_data/types.d.ts" />

// BANQ-03 : opérations importées EN ATTENTE seulement. Aucune écriture comptable.
migrate((app) => {
    try {
        app.findCollectionByNameOrId("operations_bancaires_attente");
        return;
    } catch (_) {
        // Installation initiale.
    }
    const own = '@request.auth.id != "" && utilisateur_id = @request.auth.id';
    const collection = new Collection({
        type: "base",
        name: "operations_bancaires_attente",
        listRule: own,
        viewRule: own,
        createRule: '@request.auth.id != "" && @request.body.utilisateur_id = @request.auth.id',
        updateRule: own + ' && (@request.body.utilisateur_id:isset = false || @request.body.utilisateur_id = utilisateur_id)',
        deleteRule: own,
        fields: [
            { type: "text", name: "utilisateur_id", required: true, max: 64 },
            { type: "text", name: "compte_id", required: true, max: 64 },
            { type: "text", name: "collection_compte", required: true, max: 64 },
            { type: "text", name: "source", required: true, max: 32 },
            { type: "text", name: "id_externe", required: true, max: 256 },
            { type: "text", name: "date_operation", required: true, max: 32 },
            { type: "text", name: "montant_centimes", required: true, max: 32 },
            { type: "text", name: "libelle_brut", required: true, max: 512 },
            { type: "text", name: "statut_bancaire", required: true, max: 32 },
            { type: "text", name: "decision", required: true, max: 32 },
            { type: "text", name: "tiers_id", required: false, max: 64 },
            { type: "bool", name: "verification_requise" },
            { type: "text", name: "montant_final_centimes", required: false, max: 32 }
        ],
        indexes: [
            "CREATE UNIQUE INDEX idx_ops_bancaires_externe ON operations_bancaires_attente (utilisateur_id, compte_id, source, id_externe)",
            "CREATE INDEX idx_ops_bancaires_decision ON operations_bancaires_attente (utilisateur_id, decision)"
        ]
    });
    app.save(collection);
}, (app) => {
    // Aucun effacement implicite des transactions bancaires au rollback.
});
