package sn.masae.gcm.common.exception;

/**
 * Codes d'erreur métier normalisés, exposés dans les réponses d'erreur de l'API.
 */
public enum ErrorCode {
    RESSOURCE_INTROUVABLE,
    VALIDATION,
    REGLE_METIER,
    INCOMPATIBILITE_FONCTIONS,
    COMMISSION_INCOMPLETE,
    SEUIL_REGLEMENTAIRE,
    STOCK_INSUFFISANT,
    WORKFLOW_TRANSITION_INVALIDE,
    PIECE_MANQUANTE,
    EXERCICE_CLOTURE,
    ACCES_REFUSE,
    CONFLIT
}
