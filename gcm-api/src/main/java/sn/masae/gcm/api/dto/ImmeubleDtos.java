package sn.masae.gcm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sn.masae.gcm.domain.entity.Immeuble;
import sn.masae.gcm.domain.enums.EtatBien;
import sn.masae.gcm.domain.enums.TypeImmeuble;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs du patrimoine immobilier (propriétés bâties M16, non bâties M17, baux M18). */
public final class ImmeubleDtos {

    private ImmeubleDtos() {
    }

    public record ImmeubleRequest(@NotNull Long structureId, @NotNull TypeImmeuble typeImmeuble, Long compteId, @NotBlank String designation,
                                  String localisation, String titreFoncier, BigDecimal superficie, BigDecimal superficieBatie,
                                  Integer nombrePieces, BigDecimal valeur, LocalDate dateAcquisition, String modeAcquisition,
                                  String affectation, EtatBien etat, String bailleur, BigDecimal loyerMensuel, LocalDate dateDebutBail,
                                  LocalDate dateFinBail, String observations) {
    }

    public record ImmeubleDto(Long id, String reference, Long structureId, String structureCode, TypeImmeuble typeImmeuble, String compte,
                              String designation, String localisation, String titreFoncier, BigDecimal superficie, BigDecimal superficieBatie,
                              Integer nombrePieces, BigDecimal valeur, LocalDate dateAcquisition, String modeAcquisition, String affectation,
                              EtatBien etat, String bailleur, BigDecimal loyerMensuel, LocalDate dateDebutBail, LocalDate dateFinBail,
                              String observations) {
        public static ImmeubleDto of(Immeuble i) {
            return new ImmeubleDto(i.getId(), i.getReference(), i.getStructure().getId(), i.getStructure().getCode(), i.getTypeImmeuble(),
                    i.getCompte() == null ? null : i.getCompte().getNumeroComplet(), i.getDesignation(), i.getLocalisation(), i.getTitreFoncier(),
                    i.getSuperficie(), i.getSuperficieBatie(), i.getNombrePieces(), i.getValeur(), i.getDateAcquisition(), i.getModeAcquisition(),
                    i.getAffectation(), i.getEtat(), i.getBailleur(), i.getLoyerMensuel(), i.getDateDebutBail(), i.getDateFinBail(), i.getObservations());
        }
    }
}
