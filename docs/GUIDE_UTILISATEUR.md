# Guide d'utilisation — GCM MASAE

## 1. Connexion et navigation

Connectez-vous avec l'identifiant et le mot de passe fournis par l'administrateur. Le menu de gauche est organisé en modules :

| Groupe | Écrans |
|---|---|
| Pilotage | Tableau de bord, Circuits de validation |
| Opérations | Opérations d'entrée, Mouvements internes, Opérations de sortie, Autres opérations, Existant |
| États | Livre-journal et balance, Modèles réglementaires, Reddition des comptes |
| Administration | Référentiels, Utilisateurs (administrateur) |

Les données affichées sont limitées à votre périmètre (votre structure et les structures rattachées).

## 2. Tableau de bord

Filtrez par région, département, structure et exercice. Les indicateurs présentent la valeur de l'existant, les entrées et sorties de l'exercice et les alertes :
- pièces en attente de visa externe, de scan ou de validation finale (cliquez pour ouvrir la liste) ;
- sorties provisoires en cours et retours en retard ;
- stocks sous le seuil d'alerte ;
- compte à rebours jusqu'au 30 juin N+1 (dépôt du compte de gestion) et amende encourue en cas de retard ;
- commissions de réception non constituées.

## 3. Saisir une opération

1. Ouvrez le module (par exemple **Opérations d'entrée**) et choisissez l'opération (Réception, Don, Transfert…).
2. Renseignez la structure, la nature, la date, la pièce de référence (facture, bon de livraison, décision) et le tiers.
3. Ajoutez les lignes : compte de la nomenclature, désignation, unité, quantité, prix unitaire. Pour les mouvements internes et les sorties, sélectionnez le bien dans l'existant.
4. **Enregistrer et générer la pièce** : l'opération est créée et la pièce réglementaire (modèle n°1 à n°8) est générée en brouillon.

Règles appliquées :
- au-delà de **600 000 FCFA** (services centraux) ou **300 000 FCFA** (services déconcentrés), la réception doit être prononcée par la commission complète ;
- une **affectation** porte sur un bien entré en stock et en attente d'affectation ; une **mutation** sur un bien en service ;
- une sortie ne peut excéder la quantité disponible ; une **sortie provisoire** ne diminue pas l'existant comptable ;
- aucune saisie n'est possible sur un exercice clôturé.

### Sorties provisoires et retours

Une **sortie provisoire** (prêt, location, réparation, exposition — art. 54) exige un réceptionnaire et une date de retour prévue. Elle suit le circuit du **modèle n°4** (signatures du comptable et de l'ordonnateur, visa du réceptionnaire, scan) ; à la validation, la quantité passe en « sortie provisoire » sans diminuer l'existant.

L'écran **Sorties provisoires en cours** (menu Opérations, ou indicateur du tableau de bord) liste pour chaque sortie : réceptionnaire, date de retour prévue, jours de retard, et par bien les quantités sorties, retournées et restant à retourner. Filtres : non soldées, en cours, partiellement retournée, en retard, soldée.

1. Cliquez **Enregistrer le retour** : le formulaire est pré-rempli avec le reste à retourner ; réduisez les quantités pour un retour partiel.
2. Le comptable ouvre le retour et clique **Valider le retour** : les biens sont réintégrés en attente d'affectation.
3. La sortie est **soldée** quand tous ses biens sont revenus ; elle disparaît alors des alertes.

Un retour ne peut porter que sur les biens de la sortie d'origine et ne peut excéder le reste à retourner (les retours en attente de validation sont déduits). Une sortie dont la date de retour est dépassée déclenche une alerte critique.

## 4. Circuit de validation hybride

Ouvrez la pièce depuis **Circuits de validation** ou depuis l'opération. Le bandeau d'étapes indique l'état et l'encadré **Conditions de passage** liste ce qui reste à faire.

| Étape | Action | Acteur |
|---|---|---|
| Brouillon | Télécharger le PDF/DOCX, imprimer, faire signer ; **Signer** pour enregistrer chaque signature (membres de commission, comptable) | Comptable, membres de la commission |
| Signé (commission) | **Transmettre pour visa** | Comptable |
| En attente de visa externe | **Enregistrer un visa** (CBM, CRF, DGB…) avec référence, date et scan éventuel | Comptable / contrôle |
| — | **Importer le scan** de la pièce signée et visée (PDF ou image) | Comptable |
| Scan importé | **Validation finale** : l'opération impacte le stock et le livre-journal | Ordonnateur |
| Validé | **Archiver** | Comptable / ordonnateur |

À tout moment avant validation, une pièce peut être **rejetée** avec un motif, puis **reprise** pour correction. Chaque action est tracée dans l'historique ; les pièces jointes sont horodatées et identifiées par leur empreinte SHA-256.

## 5. Existant

L'écran **Existant** présente chaque bien : quantités en attente d'affectation, en service, en sortie provisoire, proposées à la réforme, valeur, détenteur et état. Cliquez sur un bien pour afficher sa **fiche de stock** et, si vous êtes comptable ou magasinier, définir son seuil d'alerte. Exports Excel et CSV disponibles.

## 6. Autres opérations

- **Recensement** : ouvrez un recensement (les quantités théoriques sont reprises de l'existant), saisissez les quantités physiques, enregistrez, puis clôturez. Les excédents deviennent des entrées et les manquants des sorties définitives après validation de la pièce.
- **Réforme** : proposez les biens hors d'usage, puis exécutez la décision (vente avec produit et quittance, destruction, cession).
- **Certificats administratifs** : constatez une perte, un vol, une destruction ou une consommation ; la sortie correspondante suit le circuit de validation.
- **Patrimoine immobilier** : enregistrez les propriétés bâties, non bâties et les bâtiments pris à bail ; éditez les sommiers (modèles n°19 à 21).

## 7. États et reddition

- **Livre-journal et balance** : consultation par structure et exercice, export Excel, édition PDF des modèles n°10 et n°22. La balance signale tout déséquilibre (existant début + entrées − sorties ≠ existant fin).
- **Modèles réglementaires** : téléchargement des 26 modèles, vierges ou pré-remplis pour les états calculés.
- **Reddition des comptes** : préparez le compte de gestion (modèle n°23), le compte central (n°24), le bordereau de centralisation (n°25) ou le compte administratif (n°26). La pièce générée suit le circuit de validation jusqu'au dépôt, au plus tard le **30 juin N+1**.

## 8. Référentiels

Consultez les structures, les acteurs et leurs fonctions, les commissions de réception, la nomenclature et les seuils. L'administrateur peut nommer un acteur (le cumul ordonnateur/comptable est refusé), constituer la commission annuelle, ajuster les seuils et ouvrir ou clôturer les exercices.
