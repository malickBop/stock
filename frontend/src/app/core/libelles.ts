/** Libellés français conformes à l'Instruction n°0001/MFB/DGB du 26 mai 2023. */
export const STATUTS_WORKFLOW: Record<string, string> = {
  BROUILLON: 'Brouillon', SIGNE_COMMISSION: 'Signé (commission)', EN_ATTENTE_VISA_EXTERNE: 'En attente de visa externe',
  SCAN_IMPORTE: 'Scan importé', VALIDATION_FINALE: 'Validé', ARCHIVE: 'Archivé', REJETE: 'Rejeté'
};
export const ETAPES_WORKFLOW = ['BROUILLON', 'SIGNE_COMMISSION', 'EN_ATTENTE_VISA_EXTERNE', 'SCAN_IMPORTE', 'VALIDATION_FINALE', 'ARCHIVE'];

export const TYPES_MOUVEMENT: Record<string, string> = {
  ENTREE: 'Entrée', SORTIE_DEFINITIVE: 'Sortie définitive', SORTIE_PROVISOIRE: 'Sortie provisoire', RETOUR: 'Retour de sortie provisoire',
  AFFECTATION: 'Affectation', REPRISE: 'Reprise', MUTATION: 'Mutation'
};
export const SOUS_TYPES: Record<string, string> = {
  ACHAT: 'Achat', DON: 'Don', LEGS: 'Legs', PRODUCTION: 'Production', TRANSFERT_RECU: 'Transfert reçu', EXCEDENT_INVENTAIRE: 'Excédent d\'inventaire',
  REPORT_A_NOUVEAU: 'Report à nouveau', CONSOMMATION: 'Consommation', VENTE: 'Vente', DESTRUCTION: 'Destruction', CESSION: 'Cession', PERTE: 'Perte',
  VOL: 'Vol', TRANSFERT_EMIS: 'Transfert émis', MANQUANT_INVENTAIRE: 'Manquant d\'inventaire', PRET: 'Prêt', REPARATION: 'Réparation',
  EXPOSITION: 'Exposition', LOCATION: 'Location', RETOUR_PRET: 'Retour', AFFECTATION_DETENTEUR: 'Affectation à un détenteur', REPRISE_DETENTEUR: 'Reprise au détenteur',
  MUTATION_INTERNE: 'Mutation'
};

export interface Operation { cle: string; titre: string; modele: string; type: string; sousTypes: string[]; aBien: boolean; }

/** Modules de saisie, regroupés comme dans le progiciel DMTA/DGB. */
export const MODULES: Record<string, { titre: string; operations: Operation[] }> = {
  entrees: {
    titre: 'Opérations d\'entrée', operations: [
      { cle: 'achat', titre: 'Réception (achat)', modele: 'Modèle n°1 — Procès-verbal de réception', type: 'ENTREE', sousTypes: ['ACHAT'], aBien: false },
      { cle: 'don', titre: 'Don, legs, production', modele: 'Modèle n°2 — Bon d\'entrée dans l\'existant', type: 'ENTREE', sousTypes: ['DON', 'LEGS', 'PRODUCTION'], aBien: false },
      { cle: 'transfert', titre: 'Transfert reçu', modele: 'Modèle n°2 — Bon d\'entrée dans l\'existant', type: 'ENTREE', sousTypes: ['TRANSFERT_RECU'], aBien: false }
    ]
  },
  internes: {
    titre: 'Mouvements internes', operations: [
      { cle: 'affectation', titre: 'Affectation', modele: 'Modèle n°7 — Bordereau d\'affectation', type: 'AFFECTATION', sousTypes: ['AFFECTATION_DETENTEUR'], aBien: true },
      { cle: 'reprise', titre: 'Reprise', modele: 'Modèle n°7 — Bordereau d\'affectation (reprise)', type: 'REPRISE', sousTypes: ['REPRISE_DETENTEUR'], aBien: true },
      { cle: 'mutation', titre: 'Mutation', modele: 'Modèle n°8 — Bordereau de mutation des matières', type: 'MUTATION', sousTypes: ['MUTATION_INTERNE'], aBien: true }
    ]
  },
  sorties: {
    titre: 'Opérations de sortie', operations: [
      { cle: 'definitive', titre: 'Sortie définitive', modele: 'Modèle n°3 — Bon de sortie définitive de l\'existant', type: 'SORTIE_DEFINITIVE',
        sousTypes: ['CONSOMMATION', 'CESSION', 'TRANSFERT_EMIS', 'PERTE', 'VOL'], aBien: true },
      { cle: 'provisoire', titre: 'Sortie provisoire', modele: 'Modèle n°4 — Bon de sortie provisoire', type: 'SORTIE_PROVISOIRE',
        sousTypes: ['PRET', 'REPARATION', 'EXPOSITION', 'LOCATION'], aBien: true },
      { cle: 'retour', titre: 'Retour de sortie provisoire', modele: 'Réintégration rattachée au bon de sortie provisoire (modèle n°4), validée par le comptable', type: 'RETOUR', sousTypes: ['RETOUR_PRET'], aBien: true }
    ]
  }
};

export const ROLES: Record<string, string> = {
  ADMIN: 'Administrateur', ORDONNATEUR_PRINCIPAL: 'Ordonnateur principal', ORDONNATEUR_DELEGUE: 'Ordonnateur délégué',
  ORDONNATEUR_SECONDAIRE: 'Ordonnateur secondaire', COMPTABLE_ORDRE: 'Comptable d\'ordre', COMPTABLE_PRINCIPAL: 'Comptable principal des matières',
  COMPTABLE_SECONDAIRE: 'Comptable secondaire des matières', MAGASINIER: 'Magasinier-fichiste', DETENTEUR: 'Détenteur', MEMBRE_COMMISSION: 'Membre de commission',
  CONTROLE: 'Contrôle (CBM / Inspection)', CONSULTATION: 'Consultation'
};
export const FONCTIONS: Record<string, string> = {
  ORDONNATEUR_PRINCIPAL: 'Ordonnateur principal', ORDONNATEUR_DELEGUE: 'Ordonnateur délégué', ORDONNATEUR_SECONDAIRE: 'Ordonnateur secondaire',
  COMPTABLE_ORDRE: 'Comptable d\'ordre', COMPTABLE_PRINCIPAL: 'Comptable principal', COMPTABLE_SECONDAIRE: 'Comptable secondaire', MAGASINIER: 'Magasinier-fichiste',
  DETENTEUR: 'Détenteur', UTILISATEUR_FINAL: 'Utilisateur final', CONTROLEUR_BUDGETAIRE: 'Contrôleur budgétaire', INSPECTEUR_VERIFICATEUR: 'Inspecteur vérificateur'
};
export const ROLES_SIGNATURE: Record<string, string> = {
  PRESIDENT_COMMISSION: 'Président de la commission', SUPPLEANT_COMMISSION: 'Suppléant', MEMBRE_COMMISSION: 'Membre de la commission',
  COMPTABLE: 'Comptable des matières', ORDONNATEUR: 'Ordonnateur', MAGASINIER: 'Magasinier', DETENTEUR: 'Détenteur', RECEPTIONNAIRE: 'Réceptionnaire'
};
export const TYPES_VISA: Record<string, string> = {
  CBM: 'Contrôleur budgétaire (CBM)', CRF: 'Contrôleur régional des finances (CRF)', DGB: 'Direction générale du Budget',
  INSPECTEUR_VERIFICATEUR: 'Inspecteur vérificateur', RECEPTIONNAIRE: 'Réceptionnaire'
};
export const ACTIONS: Record<string, string> = {
  SIGNER: 'Signer', TRANSMETTRE: 'Transmettre pour visa', ENREGISTRER_VISA: 'Enregistrer un visa', IMPORTER_SCAN: 'Importer le scan', VALIDER: 'Validation finale',
  ARCHIVER: 'Archiver', REJETER: 'Rejeter', REPRENDRE: 'Reprendre'
};
export const ETATS_BIEN = ['NEUF', 'BON', 'PASSABLE', 'MAUVAIS', 'HORS_USAGE'];
export const ETATS_BIEN_LIB: Record<string, string> = { NEUF: 'Neuf', BON: 'Bon', PASSABLE: 'Passable', MAUVAIS: 'Mauvais', HORS_USAGE: 'Hors d\'usage' };
export const TYPES_IMMEUBLE: Record<string, string> = { BATI: 'Propriété bâtie', NON_BATI: 'Propriété non bâtie', BAIL: 'Bâtiment pris à bail' };
export const OBJETS_CERTIFICAT: Record<string, string> = {
  PERTE: 'Perte', VOL: 'Vol', DESTRUCTION: 'Destruction', CONSOMMATION: 'Consommation', REGULARISATION: 'Régularisation'
};
export const DECISIONS_REFORME: Record<string, string> = { VENTE: 'Vente aux enchères', DESTRUCTION: 'Destruction', CESSION: 'Cession gratuite' };
export const TYPES_REDDITION: Record<string, string> = {
  COMPTE_GESTION: 'Compte de gestion (Modèle n°24)', COMPTE_CENTRAL: 'Compte central (Modèle n°25)', COMPTE_ADMINISTRATIF: 'Compte administratif (Modèle n°26)',
  BORDEREAU_CENTRALISATION: 'Bordereau de centralisation (Modèle n°23)'
};
export const ETATS_SORTIE_PROVISOIRE: Record<string, string> = {
  EN_COURS: 'En cours', PARTIELLEMENT_RETOURNEE: 'Partiellement retournée', EN_RETARD: 'En retard', SOLDEE: 'Soldée'
};

export const STATUTS_OPERATION: Record<string, string> = {
  BROUILLON: 'Brouillon', EN_COURS: 'En cours', VALIDE: 'Validé', CLOTURE: 'Clôturé', ANNULE: 'Annulé'
};
