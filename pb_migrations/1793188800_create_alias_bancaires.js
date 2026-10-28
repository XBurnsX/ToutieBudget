/// <reference path="../pb_data/types.d.ts" />

// BANQ-02 : collection distincte de règles d'alias, PAS de fiches de marchands.
migrate((app) => {
    // S'arrêter si la collection existe (migration réappliquée ou installation manuelle).
    try {
        app.findCollectionByNameOrId("alias_bancaires");
        return;
    } catch (_) {
        // Collection absente : la créer.
    }

    const proprietaire = '@request.auth.id != "" && utilisateur_id = @request.auth.id';
    const collection = new Collection({
        type: "base",
        name: "alias_bancaires",
        listRule: proprietaire,
        viewRule: proprietaire,
        createRule: '@request.auth.id != "" && @request.body.utilisateur_id = @request.auth.id',
        updateRule: proprietaire + ' && @request.body.utilisateur_id:changed = false',
        deleteRule: proprietaire,
        fields: [
            { type: "text", name: "utilisateur_id", required: true, max: 64 },
            { type: "text", name: "compte_id", required: false, max: 64 },
            { type: "text", name: "libelle_brut", required: true, max: 512 },
            { type: "text", name: "tiers_id", required: true, max: 64 },
            { type: "bool", name: "active" }
        ],
        indexes: [
            "CREATE INDEX idx_alias_bancaires_utilisateur ON alias_bancaires (utilisateur_id)"
        ]
    });
    app.save(collection);
}, (app) => {
    // Ne pas supprimer automatiquement les règles pendant une rétrogradation :
    // un rollback des données bancaires doit être décidé explicitement.
});
