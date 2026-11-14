# Analyse du périmètre — GCM MASAE

Sources analysées : TDR MASAE (spécifications techniques), TDR DAGE v0.2 (`TDR_Comptabilite.docx`),
`instructions.docx` (circuits hybrides), `modèles_comptabilite_matieres.xlsx` (référentiels + Modèle_01..26),
`Prototype_suivi_comptabilite_matieres.xlsx` (logique de calcul), captures GCM DMTA/DGB.

## Référentiels (classeur Excel)
- **Structures** : STR-001 DAGE (Principale) ; STR-002..STR-026 secondaires (rattachement colonne D).
  Comptables nommés : STR-001 Zorobabel MALOU (671.229/I), STR-005 El Hadji Mansour SAMBE (734.245/E),
  STR-011 Mamadou Lamine SANE (688 868/I), STR-013 Cheikhou Oumar FAYE (762.416/F), STR-026 Seynabou Ndour GUEYE (710.595/E).
- **Acteurs** : Ordonnateur principal Cheikhou Oumar Ba (Ministre), Ordonnateur délégué Diouf Mamadou Lamine (Directeur DAGE),
  Comptable principal Zorobabel MALOU. Autres rôles « À renseigner via la plateforme ».
- **Nomenclature** : Groupe(1)-Principal(2)-Secondaire(3)-Divisionnaire(4)-Sous-compte(4). 7 comptes initiaux
  (1-21-110 Mobilier, 1-21-120 Informatique, 1-21-130 Roulant, 1-22-110 Logiciels, 2-31-110 Fournitures, 2-31-120 Carburant, 2-31-130 Consommables informatiques).
- **Seuils** (art. 44-45) : 600 000 FCFA services centraux, 300 000 FCFA services déconcentrés.

## Règles de calcul (prototype)
- Existant = Σ entrées validées − Σ sorties définitives validées (les sorties provisoires ne diminuent pas l'existant).
- Ventilation : en attente d'affectation / en service / en sortie provisoire.
- Écart recensement = physique − théorique ; > 0 excédent (entrée), < 0 manquant (sortie définitive).
- Échéance reddition : 30/06/N+1 ; « À PRÉPARER » à J-30 ; « EN RETARD » au-delà. Amende 50 000 FCFA / mois.

## Circuits hybrides (instructions.docx)
| Document | Signatures internes | Visa externe (papier → scan) | Validation finale |
|---|---|---|---|
| PV réception (M1) | Commission (Président, Suppléant, 2 membres) | CBM ou CRF | Ordonnateur |
| Bons de sortie (M3/M4) | Comptable puis Ordonnateur | Récépissé du réceptionnaire | Comptable (enregistrement du scan) |
| PV réforme (M5) | Commission | CBM/CRF + Autorité compétente (DGB) | Ordonnateur |
| PV recensement (M20) | Commission + Comptable + Ordonnateur | Inspecteur vérificateur | Ordonnateur |

États : BROUILLON → SIGNE_COMMISSION → EN_ATTENTE_VISA_EXTERNE → SCAN_IMPORTE → VALIDATION_FINALE → ARCHIVE.

## Organisation
- Un seul ordonnateur principal + un ordonnateur délégué ; un comptable des matières par structure.
- Une commission par structure et par exercice (réception, recensement, réforme), 4 membres obligatoires.
- Incompatibilités (art. 18, 29, 32, 36) : cumul ordonnateur/comptable bloqué ; lien de parenté ordonnateur ↔ comptable d'une même structure = alerte.
