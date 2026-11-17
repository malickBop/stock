package sn.masae.gcm.api.dto;

import sn.masae.gcm.domain.entity.EcritureJournal;
import sn.masae.gcm.domain.enums.StatutOperation;
import sn.masae.gcm.domain.enums.TypeReddition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs des états comptables (journal, grand-livre, balance, fiche de stock, reddition). */
public final class EtatDtos {

    private EtatDtos() {
    }

    public record EcritureDto(Long id, String structureCode, Long numeroOrdre, LocalDate date, String compte, String libelle,
                              String referencePiece, String numeroInventaire, BigDecimal quantiteEntree, BigDecimal montantEntree,
                              BigDecimal quantiteSortie, BigDecimal montantSortie) {
        public static EcritureDto of(EcritureJournal e) {
            return new EcritureDto(e.getId(), e.getStructure().getCode(), e.getNumeroOrdre(), e.getDateEcriture(), e.getCompte().getNumeroComplet(),
                    e.getLibelle(), e.getReferencePiece(), e.getBien() == null ? null : e.getBien().getNumeroInventaire(),
                    nz(e.getQuantiteEntree()), nz(e.getMontantEntree()), nz(e.getQuantiteSortie()), nz(e.getMontantSortie()));
        }

        private static BigDecimal nz(BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v;
        }
    }

    /** Ligne de balance / compte de gestion : existant début + entrées − sorties = existant fin. */
    public record LigneBalance(String compte, String libelle, Integer groupe, BigDecimal qteDebut, BigDecimal valeurDebut,
                               BigDecimal qteEntrees, BigDecimal valeurEntrees, BigDecimal qteSorties, BigDecimal valeurSorties,
                               BigDecimal qteFin, BigDecimal valeurFin) {
    }

    public record Balance(Integer exercice, String perimetre, List<LigneBalance> lignes, LigneBalance total, boolean equilibree,
                          List<String> anomalies) {
    }

    public record FicheStock(Long bienId, String numeroInventaire, String designation, String compte, String natureUnite,
                             BigDecimal qteDebut, List<EcritureDto> mouvements, BigDecimal qteFin, BigDecimal valeurFin) {
    }

    public record RedditionDto(Long id, String structureCode, Integer exercice, TypeReddition type, StatutOperation statut,
                               LocalDate dateDepot, Long documentId, LocalDate echeance, String statutEcheance) {
    }
}
