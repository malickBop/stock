package sn.masae.gcm.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs des opérations : recensement, réforme, certificat administratif. */
public final class OperationDtos {

    private OperationDtos() {
    }

    public record RecensementRequest(@NotNull Long structureId, Integer exercice, @NotNull LocalDate dateDebut, String observations) {
    }

    public record ComptageRequest(@NotNull Long ligneId, @NotNull @PositiveOrZero BigDecimal qtePhysique, EtatBien etat, String observation) {
    }

    public record RecensementLigneDto(Long id, Long bienId, String numeroInventaire, String designation, String compte, String natureUnite,
                                      BigDecimal qteTheorique, BigDecimal qtePhysique, BigDecimal ecart, BigDecimal prixUnitaire,
                                      BigDecimal valeurEcart, String nature, EtatBien etat, String observation) {
        public static RecensementLigneDto of(RecensementLigne l) {
            BigDecimal ecart = l.getEcart();
            return new RecensementLigneDto(l.getId(), l.getBien().getId(), l.getBien().getNumeroInventaire(), l.getBien().getDesignation(),
                    l.getBien().getCompte().getNumeroComplet(), l.getBien().getNatureUnite(), l.getQteTheorique(), l.getQtePhysique(), ecart,
                    l.getPrixUnitaire(), ecart.multiply(l.getPrixUnitaire()),
                    l.getQtePhysique() == null ? "NON_COMPTE" : ecart.signum() > 0 ? "EXCEDENT" : ecart.signum() < 0 ? "MANQUANT" : "CONFORME",
                    l.getEtat(), l.getObservation());
        }
    }

    public record RecensementDto(Long id, String numero, Long structureId, String structureCode, Integer exercice, LocalDate dateDebut,
                                 LocalDate dateFin, StatutOperation statut, Long documentId, StatutWorkflow statutDocument,
                                 List<RecensementLigneDto> lignes) {
    }

    public record ReformeLigneRequest(@NotNull Long bienId, @NotNull @Positive BigDecimal quantite, EtatBien etat, String motif) {
    }

    public record ReformeRequest(@NotNull Long structureId, Integer exercice, @NotNull LocalDate dateProposition, @NotBlank String motif,
                                 @NotEmpty List<@Valid ReformeLigneRequest> lignes) {
    }

    public record ExecutionReformeRequest(@NotNull DecisionReforme decision, @NotNull LocalDate dateExecution, BigDecimal produitVente,
                                          String referenceQuittance) {
    }

    public record ReformeLigneDto(Long id, Long bienId, String numeroInventaire, String designation, BigDecimal quantite,
                                  BigDecimal valeurUnitaire, EtatBien etat, String motif) {
        public static ReformeLigneDto of(ReformeLigne l) {
            return new ReformeLigneDto(l.getId(), l.getBien().getId(), l.getBien().getNumeroInventaire(), l.getBien().getDesignation(),
                    l.getQuantite(), l.getValeurUnitaire(), l.getEtat(), l.getMotif());
        }
    }

    public record ReformeDto(Long id, String numero, String structureCode, Integer exercice, LocalDate dateProposition, String motif,
                             StatutOperation statut, DecisionReforme decision, LocalDate dateExecution, BigDecimal produitVente,
                             Long documentReformeId, Long documentVenteId, List<ReformeLigneDto> lignes) {
    }

    public record CertificatRequest(@NotNull Long structureId, Integer exercice, @NotNull ObjetCertificat objet, @NotBlank String motif,
                                    @NotNull LocalDate dateCertificat, @NotEmpty @Valid List<MouvementDtos.LigneRequest> lignes) {
    }

    public record CertificatDto(Long id, String numero, String structureCode, Integer exercice, ObjetCertificat objet, String motif,
                                LocalDate dateCertificat, StatutOperation statut, Long mouvementId, Long documentId, BigDecimal montant) {
    }
}
