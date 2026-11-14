export interface Utilisateur {
  id: number; login: string; nomComplet: string; email?: string; structureId: number; structureCode: string; structureLibelle: string;
  acteurId?: number; roles: string[]; actif: boolean; derniereConnexion?: string;
}
export interface Structure {
  id: number; code: string; libelle: string; sigle?: string; typeStructure: string; typeService: string; parentId?: number; parentCode?: string;
  region?: string; departement?: string; active: boolean;
}
export interface Exercice { id: number; annee: number; statut: string; dateCloture?: string; echeanceReddition: string; }
export interface Compte {
  id: number; numeroComplet: string; groupe: number; comptePrincipal: string; compteSecondaire: string; compteDivisionnaire: string; sousCompte: string;
  libelle: string; natureUnite?: string;
}
export interface Seuil { id: number; code: string; libelle: string; typeService: string; montant: number; referenceTexte?: string; }
export interface Fonction { id: number; fonction: string; structureId: number; structureCode: string; referenceActe?: string; dateNomination?: string; actif: boolean; }
export interface Acteur {
  id: number; matricule?: string; nom: string; prenom?: string; nomComplet: string; titre?: string; email?: string; telephone?: string; actif: boolean;
  fonctions: Fonction[];
}
export interface Membre { id: number; acteurId: number; nomComplet: string; roleCommission: string; rang: number; }
export interface Commission {
  id: number; structureId: number; structureCode: string; exercice: number; referenceDecision?: string; dateDecision?: string; complete: boolean; membres: Membre[];
}
export interface Bien {
  id: number; numeroInventaire: string; designation: string; compteId: number; compte: string; compteLibelle: string; structureId: number; structureCode: string;
  groupe: number; natureUnite?: string; prixUnitaire: number; qteAttente: number; qteService: number; qteSortieProvisoire: number; qteReforme: number;
  existant: number; valeur: number; seuilAlerte?: number; detenteurId?: number; detenteur?: string; localisation?: string; etat?: string; marque?: string;
  numeroSerie?: string; dateAcquisition?: string;
}
export interface LigneMouvement {
  id?: number; bienId?: number | null; numeroInventaire?: string; compteId?: number | null; compte?: string; designation: string; quantite: number | null;
  natureUnite?: string; prixUnitaire: number | null; montant?: number; etat?: string; observation?: string;
}
export interface Mouvement {
  id: number; numero: string; structureId: number; structureCode: string; exercice: number; typeMouvement: string; sousType: string; statut: string;
  dateMouvement: string; referencePiece?: string; tiers?: string; structureDestination?: string; detenteurSource?: string; detenteurDestination?: string;
  receptionnaire?: string; dateRetourPrevue?: string; mouvementOrigineId?: number; montantTotal: number; observations?: string; documentId?: number;
  statutDocument?: string; lignes: LigneMouvement[];
}
export interface Signature { id: number; roleSignature: string; signataireNom: string; dateSignature: string; }
export interface Visa { id: number; typeVisa: string; autorite?: string; referenceVisa?: string; dateVisa: string; pieceJointeId?: number; }
export interface Piece { id: number; typePiece: string; nomFichier: string; contentType: string; taille: number; sha256: string; dateDepot: string; deposePar: string; }
export interface Historique { ancienStatut?: string; nouveauStatut: string; action: string; utilisateur: string; commentaire?: string; date: string; }
export interface Exigence { libelle: string; satisfaite: boolean; }
export interface DocumentReglementaire {
  id: number; modele: number; numero: string; titre: string; structureId: number; structureCode: string; exercice: number; statut: string;
  requiertCommission: boolean; montantTotal?: number; numeroVersion: number; mouvementId?: number; recensementId?: number; reformeId?: number;
  certificatId?: number; dateCreation: string; auteur: string; dateValidationFinale?: string; validePar?: string; motifRejet?: string;
  signatures: Signature[]; visas: Visa[]; pieces: Piece[]; exigences: Exigence[]; actionsPossibles: string[];
}
export interface DocumentResume {
  id: number; modele: number; numero: string; titre: string; structureCode: string; statut: string; montantTotal?: number; dateCreation: string; auteur: string;
}
export interface Alerte { type: string; niveau: string; message: string; structureCode?: string; referenceId?: number; lien?: string; }
export interface IndicateurStructure {
  structureId: number; code: string; libelle: string; region?: string; nbBiens: number; valeurExistant: number; documentsEnCircuit: number;
  sortiesProvisoires: number; statutCompteGestion: string;
}
export interface Dashboard {
  exercice: number; echeanceReddition: string; joursRestants: number; statutEcheance: string; amendeEncourue: number; nbStructures: number; nbBiens: number;
  valeurExistant: number; valeurEntrees: number; valeurSorties: number; documentsEnCircuit: number; enAttenteVisa: number; enAttenteScan: number;
  enAttenteValidation: number; sortiesProvisoiresEnCours: number; biensSousSeuil: number; repartition: { compte: string; libelle: string; valeur: number }[];
  structures: IndicateurStructure[]; alertes: Alerte[];
}
export interface Ecriture {
  id: number; structureCode: string; numeroOrdre: number; date: string; compte: string; libelle: string; referencePiece?: string; numeroInventaire?: string;
  quantiteEntree: number; montantEntree: number; quantiteSortie: number; montantSortie: number;
}
export interface LigneBalance {
  compte: string; libelle: string; groupe?: number; qteDebut: number; valeurDebut: number; qteEntrees: number; valeurEntrees: number; qteSorties: number;
  valeurSorties: number; qteFin: number; valeurFin: number;
}
export interface Balance { exercice: number; perimetre: string; lignes: LigneBalance[]; total: LigneBalance; equilibree: boolean; anomalies: string[]; }
export interface FicheStock {
  bienId: number; numeroInventaire: string; designation: string; compte: string; natureUnite?: string; qteDebut: number; mouvements: Ecriture[]; qteFin: number;
  valeurFin: number;
}
export interface Reddition {
  id: number; structureCode: string; exercice: number; type: string; statut: string; dateDepot?: string; documentId?: number; echeance: string; statutEcheance: string;
}
export interface RecensementLigne {
  id: number; bienId: number; numeroInventaire: string; designation: string; compte: string; natureUnite?: string; qteTheorique: number; qtePhysique?: number;
  ecart?: number; prixUnitaire: number; valeurEcart?: number; nature?: string; etat?: string; observation?: string;
}
export interface Recensement {
  id: number; numero: string; structureId: number; structureCode: string; exercice: number; dateDebut: string; dateFin?: string; statut: string;
  documentId?: number; statutDocument?: string; lignes: RecensementLigne[];
}
export interface Reforme {
  id: number; numero: string; structureCode: string; exercice: number; dateProposition: string; motif: string; statut: string; decision?: string;
  dateExecution?: string; produitVente?: number; documentReformeId?: number; documentVenteId?: number;
  lignes: { id: number; bienId: number; numeroInventaire: string; designation: string; quantite: number; valeurUnitaire: number; etat?: string; motif?: string }[];
}
export interface Certificat {
  id: number; numero: string; structureCode: string; exercice: number; objet: string; motif: string; dateCertificat: string; statut: string;
  mouvementId?: number; documentId?: number; montant: number;
}
export interface Immeuble {
  id: number; reference: string; structureId: number; structureCode: string; typeImmeuble: string; designation: string; localisation?: string;
  titreFoncier?: string; superficie?: number; superficieBatie?: number; nombrePieces?: number; valeur?: number; dateAcquisition?: string;
  modeAcquisition?: string; affectation?: string; etat?: string; bailleur?: string; loyerMensuel?: number; dateDebutBail?: string; dateFinBail?: string;
  observations?: string;
}
export interface ModeleInfo { numero: number; titre: string; etat: boolean; }
