package sn.masae.gcm.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs des biens et des mouvements. */
public final class MouvementDtos {

    private MouvementDtos() {
    }

    public record BienDto(Long id, String numeroInventaire, String designation, Long compteId, String compte, String compteLibelle,
                          Long structureId, String structureCode, Integer groupe, String natureUnite, BigDecimal prixUnitaire,
                          BigDecimal qteAttente, BigDecimal qteService, BigDecimal qteSortieProvisoire, BigDecimal qteReforme,
                          BigDecimal existant, BigDecimal valeur, BigDecimal seuilAlerte, Long detenteurId, String detenteur,
                          String localisation, EtatBien etat, String marque, String numeroSerie, LocalDate dateAcquisition) {
        public static BienDto of(Bien b) {
            return new BienDto(b.getId(), b.getNumeroInventaire(), b.getDesignation(), b.getCompte().getId(), b.getCompte().getNumeroComplet(),
                    b.getCompte().getLibelle(), b.getStructure().getId(), b.getStructure().getCode(), b.getGroupe(), b.getNatureUnite(),
                    b.getPrixUnitaire(), b.getQteAttente(), b.getQteService(), b.getQteSortieProvisoire(), b.getQteReforme(),
                    b.getExistant(), b.getValeurExistant(), b.getSeuilAlerte(),
                    b.getDetenteur() == null ? null : b.getDetenteur().getId(), b.getDetenteur() == null ? null : b.getDetenteur().getNomComplet(),
                    b.getLocalisation(), b.getEtat(), b.getMarque(), b.getNumeroSerie(), b.getDateAcquisition());
        }
    }

    public record BienUpdateRequest(BigDecimal seuilAlerte, String localisation, EtatBien etat, String marque, String numeroSerie) {
    }

    public record LigneRequest(Long bienId, Long compteId, String designation, @NotNull @Positive BigDecimal quantite, String natureUnite,
                               @PositiveOrZero BigDecimal prixUnitaire, EtatBien etat, String marque, String numeroSerie, String observation) {
    }

    public record MouvementRequest(@NotNull Long structureId, Integer exercice, @NotNull TypeMouvement typeMouvement,
                                   @NotNull SousTypeMouvement sousType, @NotNull LocalDate dateMouvement, String referencePiece,
                                   String tiers, Long structureDestinationId, Long detenteurSourceId, Long detenteurDestinationId,
                                   String receptionnaire, LocalDate dateRetourPrevue, Long mouvementOrigineId, String observations,
                                   @NotEmpty List<@Valid LigneRequest> lignes) {
    }

    public record LigneSuiviDto(Long bienId, String numeroInventaire, String designation, String natureUnite, BigDecimal prixUnitaire,
                                BigDecimal qteSortie, BigDecimal qteRetournee, BigDecimal reste) {
    }

    /** Suivi d'une sortie provisoire : état EN_COURS, PARTIELLEMENT_RETOURNEE, EN_RETARD ou SOLDEE. */
    public record SortieProvisoireDto(Long id, String numero, Long structureId, String structureCode, SousTypeMouvement sousType,
                                      String receptionnaire, LocalDate dateMouvement, LocalDate dateRetourPrevue, long joursRetard, String etat,
                                      BigDecimal montantTotal, Long retourEnAttenteId, List<LigneSuiviDto> lignes) {
    }

    public record LigneDto(Long id, Long bienId, String numeroInventaire, String compte, String designation, BigDecimal quantite,
                           String natureUnite, BigDecimal prixUnitaire, BigDecimal montant, EtatBien etat, String observation) {
        public static LigneDto of(LigneMouvement l) {
            return new LigneDto(l.getId(), l.getBien() == null ? null : l.getBien().getId(),
                    l.getBien() == null ? null : l.getBien().getNumeroInventaire(), l.getCompte().getNumeroComplet(), l.getDesignation(),
                    l.getQuantite(), l.getNatureUnite(), l.getPrixUnitaire(), l.getMontant(), l.getEtat(), l.getObservation());
        }
    }

    public record MouvementDto(Long id, String numero, Long structureId, String structureCode, Integer exercice, TypeMouvement typeMouvement,
                               SousTypeMouvement sousType, StatutMouvement statut, LocalDate dateMouvement, String referencePiece,
                               String tiers, String structureDestination, String detenteurSource, String detenteurDestination,
                               String receptionnaire, LocalDate dateRetourPrevue, Long mouvementOrigineId, BigDecimal montantTotal,
                               String observations, Long documentId, StatutWorkflow statutDocument, java.time.Instant dateValidation,
                               List<LigneDto> lignes) {
        public static MouvementDto of(Mouvement m, DocumentReglementaire doc) {
            return new MouvementDto(m.getId(), m.getNumero(), m.getStructure().getId(), m.getStructure().getCode(), m.getExercice().getAnnee(),
                    m.getTypeMouvement(), m.getSousType(), m.getStatut(), m.getDateMouvement(), m.getReferencePiece(), m.getTiers(),
                    m.getStructureDestination() == null ? null : m.getStructureDestination().getCode(),
                    m.getDetenteurSource() == null ? null : m.getDetenteurSource().getNomComplet(),
                    m.getDetenteurDestination() == null ? null : m.getDetenteurDestination().getNomComplet(),
                    m.getReceptionnaire(), m.getDateRetourPrevue(), m.getMouvementOrigine() == null ? null : m.getMouvementOrigine().getId(),
                    m.getMontantTotal(), m.getObservations(), doc == null ? null : doc.getId(), doc == null ? null : doc.getStatut(), m.getDateValidation(),
                    m.getLignes().stream().map(LigneDto::of).toList());
        }
    }
}
