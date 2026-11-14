package sn.masae.gcm.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Indicateurs du tableau de bord. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Alerte(String type, String niveau, String message, String structureCode, Long referenceId, String lien) {
    }

    public record IndicateurStructure(Long structureId, String code, String libelle, String region, long nbBiens, BigDecimal valeurExistant,
                                      long documentsEnCircuit, long sortiesProvisoires, String statutCompteGestion) {
    }

    public record RepartitionCompte(String compte, String libelle, BigDecimal valeur) {
    }

    public record Dashboard(Integer exercice, LocalDate echeanceReddition, long joursRestants, String statutEcheance, long amendeEncourue,
                            long nbStructures, long nbBiens, BigDecimal valeurExistant, BigDecimal valeurEntrees, BigDecimal valeurSorties,
                            long documentsEnCircuit, long enAttenteVisa, long enAttenteScan, long enAttenteValidation,
                            long sortiesProvisoiresEnCours, long biensSousSeuil, List<RepartitionCompte> repartition,
                            List<IndicateurStructure> structures, List<Alerte> alertes) {
    }
}
