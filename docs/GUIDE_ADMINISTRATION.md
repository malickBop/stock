# Guide d'administration — GCM MASAE

## 1. Installation

### Prérequis
- Docker 24+ et Docker Compose v2 ; 2 vCPU, 4 Go de RAM, 20 Go de disque (base + GED).
- Un nom de domaine et un certificat TLS en production (reverse proxy en frontal du service `web`).

### Déploiement
```bash
git clone <dépôt> gcm && cd gcm
cp .env.example .env
# Renseigner :
#   POSTGRES_PASSWORD   mot de passe de la base
#   GCM_JWT_SECRET      secret JWT de 32 octets minimum : openssl rand -base64 48
#   GCM_DEMO_PASSWORD   mot de passe initial des comptes créés par le seed
docker compose up -d --build
docker compose logs -f api      # attendre « Seed GCM : 26 structures, … »
```

Services :

| Service | Rôle | Volume |
|---|---|---|
| `db` | PostgreSQL 16 | `pgdata` |
| `api` | API Spring Boot (port interne 8080) | `ged` (pièces scannées) |
| `web` | Interface Angular servie par nginx, proxy `/api` | — |

### Variables de configuration de l'API

| Variable | Défaut | Description |
|---|---|---|
| `GCM_DB_URL` / `GCM_DB_USER` / `GCM_DB_PASSWORD` | `jdbc:postgresql://localhost:5432/gcm` | Connexion PostgreSQL |
| `GCM_JWT_SECRET` | valeur de développement | **Obligatoire en production** |
| `GCM_STOCKAGE_DIR` | `./data/ged` | Répertoire de la GED |
| `GCM_SEED` | `true` | Import des référentiels au premier démarrage (base vide) |
| `GCM_DEMO_PASSWORD` | valeur de développement | Mot de passe initial des comptes du seed |
| `GCM_PORT` | `8080` | Port HTTP de l'API |

## 2. Initialisation des données

Au premier démarrage, Liquibase crée le schéma (5 jeux de migrations : référentiels, patrimoine et mouvements, documents et workflow, opérations, audit et immuabilité), puis le seed importe depuis `gcm-api/src/main/resources/seed/modeles_comptabilite_matieres.xlsx` :
- les 26 structures (`Référentiel_Structures`) et leur rattachement ;
- les acteurs pré-identifiés (`Référentiel_Acteurs`) et leurs nominations ;
- la nomenclature des comptes (`Référentiel_Nomenclature`, clé reconstituée depuis les 5 segments) ;
- les seuils réglementaires (600 000 / 300 000 FCFA), l'exercice en cours et une commission de démonstration pour STR-001.

Pour mettre à jour les référentiels, remplacez le classeur puis recréez la base, ou utilisez les écrans **Référentiels**.

Comptes créés (mot de passe `GCM_DEMO_PASSWORD`, à changer immédiatement) :

| Identifiant | Rôle | Structure |
|---|---|---|
| `admin` | Administrateur | DAGE (STR-001) |
| `ordonnateur`, `ordonnateur.delegue` | Ordonnateur principal / délégué | DAGE |
| `comptable` | Comptable principal des matières | DAGE |
| `comptable.bfpa` | Comptable secondaire | BFPA |
| `comptable.ordre` | Comptable d'ordre | DAGE |
| `magasinier` | Magasinier-fichiste | DAGE |
| `controle` | Contrôle (CBM / inspection) | DAGE |
| `commission` | Président de commission | DAGE |

En production, désactivez ou supprimez les comptes inutiles (**Administration → Utilisateurs**, case « Compte actif »).

## 3. Sécurité et habilitations

- **Rôles** : `ADMIN`, `ORDONNATEUR_PRINCIPAL`, `ORDONNATEUR_DELEGUE`, `ORDONNATEUR_SECONDAIRE`, `COMPTABLE_ORDRE`, `COMPTABLE_PRINCIPAL`, `COMPTABLE_SECONDAIRE`, `MAGASINIER`, `DETENTEUR`, `MEMBRE_COMMISSION`, `CONTROLE`, `CONSULTATION`.
- **Périmètre hiérarchique** : un utilisateur voit sa structure et les structures qui lui sont rattachées. Les profils nationaux (`ADMIN`, `ORDONNATEUR_PRINCIPAL`, `COMPTABLE_ORDRE`, `CONTROLE`) voient l'ensemble du ministère.
- **Séparation des fonctions** : un même acteur ne peut cumuler une fonction d'ordonnateur et une fonction de comptable (blocage à la nomination et à la création de compte). Un lien de parenté déclaré entre ordonnateur et comptable d'une même structure génère une alerte.
- **Commissions** : 4 membres (président, suppléant, deux membres) ; ni l'ordonnateur ni le comptable de la structure ne peuvent en faire partie.
- **Jetons** : JWT signés HMAC, durée de validité de 480 minutes (`gcm.jwt-validite-minutes`).

## 4. Exercices et clôture

- **Référentiels → Seuils et exercices** : ouvrir l'exercice N+1, clôturer l'exercice N.
- Après clôture, aucune opération ne peut plus être saisie ni validée sur l'exercice.
- Échéance de reddition : 30 juin N+1. Le tableau de bord signale l'approche de l'échéance et l'amende encourue (50 000 FCFA par mois de retard).

## 5. Interopérabilité SIGIF / SYSBUDGEP

Points d'accès en lecture seule, authentifiés par l'en-tête `X-API-Key` :

| Méthode | URL | Contenu |
|---|---|---|
| GET | `/api/interop/structures` | Structures et codes ordonnateurs |
| GET | `/api/interop/situations?exercice=2026` | Existant début, entrées, sorties, existant fin par structure |
| GET | `/api/interop/mouvements?exercice=2026&depuis=2026-06-01T00:00:00Z` | Mouvements validés (synchronisation incrémentale) |

Création d'une clé (la base ne stocke que l'empreinte SHA-256) :
```bash
CLE=$(openssl rand -hex 32); echo "Clé à transmettre au partenaire : $CLE"
HASH=$(printf %s "$CLE" | sha256sum | cut -d' ' -f1)
docker compose exec db psql -U gcm -d gcm -c \
  "INSERT INTO api_client(nom, cle_hash, description) VALUES ('SIGIF', '$HASH', 'Direction du Budget');"
```
Révocation : `UPDATE api_client SET actif = false WHERE nom = 'SIGIF';`

Chaque mouvement porte sa `dateValidation` : le partenaire conserve la plus grande reçue et la repasse en `depuis` à l'appel suivant.
Un client de référence (Python, bibliothèque standard) est fourni : `GCM_API_KEY=<clé> python3 outils/client_interop.py --url https://gcm.example --exercice 2026`.
Il synchronise les mouvements de façon incrémentale (fichier `etat_sync.json`) et contrôle existant début + entrées − sorties = existant fin pour chaque structure.

La documentation OpenAPI complète est disponible sur `/swagger-ui.html` (spécification `/v3/api-docs`).

## 6. Sauvegarde, archivage et conservation

- **Base** : `docker compose exec db pg_dump -U gcm -Fc gcm > gcm-$(date +%F).dump` (quotidien conseillé) ; restauration par `pg_restore`.
- **GED** : sauvegarder le volume `ged` (`docker run --rm -v gcm_ged:/d -v $PWD:/b alpine tar czf /b/ged-$(date +%F).tgz -C /d .`).
- Les pièces sont conservées **10 ans** ; chaque fichier est identifié par son empreinte SHA-256, vérifiable à tout moment.
- Le livre-journal, l'historique des documents et le journal d'audit sont protégés par des triggers PostgreSQL qui interdisent toute modification ou suppression.

## 7. Supervision et dépannage

| Symptôme | Vérification |
|---|---|
| L'API ne démarre pas | `docker compose logs api` : connexion base, secret JWT manquant |
| 401 sur toutes les requêtes | Jeton expiré ou `GCM_JWT_SECRET` modifié : se reconnecter |
| 403 « hors périmètre » | Structure de l'utilisateur ne couvrant pas la structure demandée |
| 409 / 422 à la validation | Règle métier bloquante : le message indique la condition manquante (visa, scan, commission, stock) |
| Santé | `GET /actuator/health` |

## 8. Tests

```bash
mvn test                       # unitaires + intégration (Docker requis pour Testcontainers)
cd frontend && npx ng build    # compilation de l'interface
cd frontend && npm run test:ci # tests Angular (Chrome headless ; CHROME_BIN si Chrome n'est pas dans le PATH)
```
Le test `ParcoursCompletIntegrationTest` déroule le parcours complet : réception au-delà du seuil, signatures de commission, visa CBM, scan, validation finale, stock, livre-journal, balance, génération PDF/DOCX, exports et préparation du compte de gestion.
Les tests Angular couvrent la session et les rôles, l'intercepteur JWT (déconnexion sur 401), les formats FCFA/quantités/dates, les libellés réglementaires, la confirmation de réception réservée au comptable destinataire et la page des utilisateurs réservée à l'administrateur.

## 9. Mise en production

1. Sur le serveur (Linux, Docker ≥ 24, 4 Go de RAM, 2 vCPU, disque sauvegardé), cloner le dépôt puis créer `.env` depuis `.env.example` :
   ```bash
   POSTGRES_PASSWORD=$(openssl rand -base64 24)
   GCM_JWT_SECRET=$(openssl rand -base64 48)
   GCM_ADMIN_PASSWORD=<mot de passe initial de l'administrateur, 12 caractères minimum>
   GCM_DEMO_PASSWORD=
   ```
2. Démarrer avec la surcharge de production :
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
   Le profil `prod` refuse de démarrer si le secret JWT fait moins de 48 octets ou reprend la valeur de développement, si le mot de passe de la base fait moins de 12 caractères, ou si un mot de passe de démonstration est renseigné. Aucun compte de démonstration n'est créé ; seul le compte `admin` l'est, avec `GCM_ADMIN_PASSWORD`. Swagger est désactivé (`GCM_SWAGGER=true` pour l'ouvrir aux partenaires).
3. Se connecter en `admin`, changer son mot de passe, puis créer les comptes nominatifs (Référentiels → Utilisateurs) et nommer les acteurs. Retirer ensuite `GCM_ADMIN_PASSWORD` du `.env`.
4. HTTPS : l'interface écoute sur `127.0.0.1:8081` (`GCM_HTTP_BIND`, `GCM_HTTP_PORT`). La publier derrière un reverse proxy TLS (nginx + certificat de l'ADIE ou Let's Encrypt), par exemple :
   ```nginx
   server {
     listen 443 ssl http2;
     server_name gcm.agriculture.gouv.sn;
     ssl_certificate     /etc/ssl/gcm/fullchain.pem;
     ssl_certificate_key /etc/ssl/gcm/privkey.pem;
     add_header Strict-Transport-Security "max-age=31536000" always;
     client_max_body_size 25m;
     location / { proxy_pass http://127.0.0.1:8081; proxy_set_header Host $host; proxy_set_header X-Forwarded-Proto https; }
   }
   ```
5. Sauvegardes quotidiennes de la base et de la GED, avec rotation sur 30 jours :
   ```bash
   0 2 * * * cd /opt/stock && outils/sauvegarde.sh /var/backups/gcm 30
   ```
   Copier `/var/backups/gcm` hors du serveur. Restauration : `pg_restore -U gcm -d gcm --clean <fichier>.dump` et extraction de l'archive GED dans le volume `ged`.
6. Clés d'interopérabilité : une clé par partenaire (§ 5), transmise par canal sécurisé.
