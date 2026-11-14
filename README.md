# GCM — Comptabilité des matières du MASAE

Application web de gestion de la comptabilité des matières du Ministère de l'Agriculture, de la Souveraineté alimentaire et de l'Élevage (Sénégal), conforme au décret n°2018-842 du 09 mai 2018 et à l'Instruction n°0001/MFB/DGB du 26 mai 2023.

## Fonctionnalités

- Référentiels : 26 structures (STR-001 à STR-026), acteurs, nominations, commissions annuelles de réception, nomenclature des comptes, seuils réglementaires — importés automatiquement depuis `modeles_comptabilite_matieres.xlsx`.
- Opérations : entrées (achat, don, legs, production, transfert), mouvements internes (affectation, reprise, mutation), sorties définitives et provisoires, retours, recensement, réforme, certificats administratifs, patrimoine immobilier.
- Circuit hybride papier/numérique : `BROUILLON → SIGNE_COMMISSION → EN_ATTENTE_VISA_EXTERNE → SCAN_IMPORTE → VALIDATION_FINALE → ARCHIVE` (et `REJETE`), avec GED (empreinte SHA-256) et historique d'audit non modifiable.
- Règles bloquantes : séparation ordonnateur/comptable, alerte de parenté, commission complète au-delà des seuils (600 000 FCFA central, 300 000 FCFA déconcentré), visa externe et scan obligatoires, stock négatif interdit, exercice clôturé.
- États : livre-journal, grand-livre, fiche de stock, balance, compte de gestion, compte central, bordereau de centralisation, compte administratif ; génération des 26 modèles réglementaires en PDF et DOCX ; exports CSV et Excel.
- Sécurité : JWT, RBAC, périmètre hiérarchique par structure ; API d'interopérabilité SIGIF/SYSBUDGEP par clé (`X-API-Key`).
- Tableau de bord filtrable (région, département, structure, exercice) avec alertes (visas, scans, sorties provisoires, seuils de stock, échéance du 30 juin).

## Démarrage rapide (Docker)

```bash
cp .env.example .env      # renseigner POSTGRES_PASSWORD, GCM_JWT_SECRET, GCM_DEMO_PASSWORD
docker compose up -d --build
```

Interface : `http://localhost` (port `GCM_HTTP_PORT`). Documentation de l'API : `/swagger-ui.html`.

## Développement

| Composant | Commande |
|---|---|
| Base PostgreSQL | `docker run -d --name gcm-pg -e POSTGRES_DB=gcm -e POSTGRES_USER=gcm -e POSTGRES_PASSWORD=gcm -p 5432:5432 postgres:16-alpine` |
| API (Java 17) | `mvn -pl gcm-api -am spring-boot:run` |
| Interface (Node 18+) | `cd frontend && npm install && npm start` (proxy `/api` → `localhost:8080`) |
| Tests | `mvn test` (les tests d'intégration Testcontainers nécessitent Docker) |

## Structure

| Module | Rôle |
|---|---|
| `gcm-common` | Exceptions métier, utilitaires |
| `gcm-domain` | Entités JPA, énumérations, dépôts |
| `gcm-documents` | Moteur de rendu PDF (OpenPDF) / DOCX (Apache POI) et métadonnées des 26 modèles |
| `gcm-api` | Services métier, workflow, sécurité, contrôleurs REST, migrations Liquibase, seed |
| `frontend` | Interface Angular 18 |

## Documentation

- [Analyse du périmètre](docs/ANALYSE_PERIMETRE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Guide d'administration](docs/GUIDE_ADMINISTRATION.md)
- [Guide d'utilisation](docs/GUIDE_UTILISATEUR.md)
