# Architecture technique — GCM MASAE

## Modules Maven (groupId `sn.masae.gcm`, Java 17, Spring Boot 3.3)
| Module | Rôle |
|---|---|
| `gcm-common` | Exceptions métier (`BusinessException`, `NotFoundException`, `IncompatibiliteException`), codes d'erreur, utilitaires (montants FCFA, dates). |
| `gcm-domain` | Entités JPA, enums, repositories Spring Data. |
| `gcm-documents` | Moteur de génération : `DocumentSpec` (modèle de mise en page) → `PdfRenderer` (OpenPDF) / `DocxRenderer` (Apache POI). 26 templates `Modele01..Modele26`. |
| `gcm-api` | Application Spring Boot : sécurité JWT/RBAC, services métier, validateurs, workflow, REST, Liquibase, seed Excel. |
| `frontend/` | Angular (standalone, Angular Material, thème vert institutionnel). |

## Modèle de données (PostgreSQL, Liquibase `db/changelog`)
Référentiels : `structure` (code STR-xxx, parent_id, type PRINCIPALE/SECONDAIRE, type_service CENTRAL/DECONCENTRE, region),
`acteur`, `acteur_fonction` (role, structure, dates nomination/accréditation, actif), `lien_parente`, `regle_incompatibilite`,
`exercice` (annee, statut OUVERT/CLOTURE), `commission` (structure+exercice unique) + `commission_membre` (PRESIDENT/SUPPLEANT/MEMBRE),
`compte_nomenclature`, `seuil_reglementaire`, `utilisateur` + `utilisateur_role` (role + structure périmètre), `api_client`.

Patrimoine & mouvements : `bien` (article : compte, structure, désignation, groupe 1 immatriculé / groupe 2 stock, nature_unite,
prix_unitaire, quantités ventilées qte_attente/qte_service/qte_sortie_provisoire/qte_proposee_reforme, détenteur courant),
`mouvement` (type ENTREE/SORTIE_DEFINITIVE/SORTIE_PROVISOIRE/RETOUR/AFFECTATION/REPRISE/MUTATION, sous_type, statut, détenteurs, réceptionnaire, date_retour_prevue),
`ligne_mouvement`, `livre_journal` (numéro ininterrompu par structure+exercice, immuable via trigger), `stock_ouverture` (report à nouveau).

Documents : `document_reglementaire` (modele 1..26, numero, statut workflow, structure, exercice, mouvement/objet lié, version),
`document_signature`, `document_visa_externe`, `document_historique`, `piece_jointe` (GED, sha256, rétention 10 ans).
Objets spécifiques : `pv_reforme_ligne`, `pv_vente_ligne`, `recensement` + `recensement_ligne`, `certificat_administratif` + lignes,
`fiche_immeuble` (BATI/NON_BATI/BAIL), `fiche_consommation` + lignes, `reddition` (compte gestion/central/administratif).

Audit : `audit_log` immuable (trigger bloquant UPDATE/DELETE).

## Workflow documentaire
BROUILLON → SIGNE_COMMISSION → EN_ATTENTE_VISA_EXTERNE → SCAN_IMPORTE → VALIDATION_FINALE → ARCHIVE (+ REJETE).
`CircuitDefinition` par modèle : rôles signataires requis, visas externes requis, scan requis, rôle de validation finale.
Les impacts comptables sont appliqués par `ImpactComptableService` à la VALIDATION_FINALE.

## Sécurité
JWT (HS256), rôles : ADMIN, ORDONNATEUR_PRINCIPAL, ORDONNATEUR_DELEGUE, COMPTABLE_ORDRE, COMPTABLE_PRINCIPAL, COMPTABLE_SECONDAIRE,
MAGASINIER, DETENTEUR, UTILISATEUR_FINAL, MEMBRE_COMMISSION, CONTROLE. Périmètre = structure de rattachement + descendants
(global pour ADMIN, ordonnateur principal, comptable d'ordre, contrôle). Clés API (`X-API-Key`) pour l'interopérabilité.
