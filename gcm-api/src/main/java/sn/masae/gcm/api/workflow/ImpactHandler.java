package sn.masae.gcm.api.workflow;

import sn.masae.gcm.domain.entity.DocumentReglementaire;

/**
 * Effet comptable déclenché à la validation finale d'un document (entrée en stock, sortie, écarts…).
 */
public interface ImpactHandler {

    boolean supporte(DocumentReglementaire document);

    void appliquer(DocumentReglementaire document);
}
