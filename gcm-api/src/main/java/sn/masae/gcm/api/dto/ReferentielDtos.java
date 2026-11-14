package sn.masae.gcm.api.dto;

import jakarta.validation.constraints.*;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs des référentiels (structures, exercices, nomenclature, seuils, acteurs, commissions). */
public final class ReferentielDtos {

    private ReferentielDtos() {
    }

    public record StructureDto(Long id, String code, String libelle, String sigle, TypeStructure typeStructure, TypeService typeService,
                               Long parentId, String parentCode, String region, String departement, boolean active) {
        public static StructureDto of(Structure s) {
            return new StructureDto(s.getId(), s.getCode(), s.getLibelle(), s.getSigle(), s.getTypeStructure(), s.getTypeService(),
                    s.getParent() == null ? null : s.getParent().getId(), s.getParent() == null ? null : s.getParent().getCode(),
                    s.getRegion(), s.getDepartement(), s.isActive());
        }
    }

    public record StructureRequest(@NotBlank @Pattern(regexp = "STR-\\d{3}") String code, @NotBlank String libelle, String sigle,
                                   @NotNull TypeService typeService, Long parentId, String region, String departement) {
    }

    public record ExerciceDto(Long id, Integer annee, StatutExercice statut, LocalDate dateCloture, LocalDate echeanceReddition) {
        public static ExerciceDto of(Exercice e) {
            return new ExerciceDto(e.getId(), e.getAnnee(), e.getStatut(), e.getDateCloture(), LocalDate.of(e.getAnnee() + 1, 6, 30));
        }
    }

    public record CompteDto(Long id, String numeroComplet, Integer groupe, String comptePrincipal, String compteSecondaire,
                            String compteDivisionnaire, String sousCompte, String libelle, String natureUnite) {
        public static CompteDto of(CompteNomenclature c) {
            return new CompteDto(c.getId(), c.getNumeroComplet(), c.getGroupe(), c.getComptePrincipal(), c.getCompteSecondaire(),
                    c.getCompteDivisionnaire(), c.getSousCompte(), c.getLibelle(), c.getNatureUnite());
        }
    }

    public record CompteRequest(@NotNull @Min(1) @Max(2) Integer groupe, @NotBlank @Pattern(regexp = "\\d{2}") String comptePrincipal,
                                @NotBlank @Pattern(regexp = "\\d{3}") String compteSecondaire,
                                @NotBlank @Pattern(regexp = "\\d{4}") String compteDivisionnaire,
                                @NotBlank @Pattern(regexp = "\\d{4}") String sousCompte, @NotBlank String libelle, String natureUnite) {
    }

    public record SeuilDto(Long id, String code, String libelle, TypeService typeService, BigDecimal montant, String referenceTexte) {
        public static SeuilDto of(SeuilReglementaire s) {
            return new SeuilDto(s.getId(), s.getCode(), s.getLibelle(), s.getTypeService(), s.getMontant(), s.getReferenceTexte());
        }
    }

    public record SeuilRequest(@NotNull @PositiveOrZero BigDecimal montant) {
    }

    public record FonctionDto(Long id, FonctionActeur fonction, Long structureId, String structureCode, String referenceActe,
                              LocalDate dateNomination, LocalDate dateAccreditation, boolean actif) {
        public static FonctionDto of(ActeurFonction f) {
            return new FonctionDto(f.getId(), f.getFonction(), f.getStructure().getId(), f.getStructure().getCode(), f.getReferenceActe(),
                    f.getDateNomination(), f.getDateAccreditation(), f.isActif());
        }
    }

    public record ActeurDto(Long id, String matricule, String nom, String prenom, String nomComplet, String titre, String email,
                            String telephone, boolean actif, List<FonctionDto> fonctions) {
        public static ActeurDto of(Acteur a, List<ActeurFonction> fonctions) {
            return new ActeurDto(a.getId(), a.getMatricule(), a.getNom(), a.getPrenom(), a.getNomComplet(), a.getTitre(), a.getEmail(),
                    a.getTelephone(), a.isActif(), fonctions.stream().map(FonctionDto::of).toList());
        }
    }

    public record ActeurRequest(String matricule, @NotBlank String nom, String prenom, String titre, @Email String email, String telephone) {
    }

    public record NominationRequest(@NotNull FonctionActeur fonction, @NotNull Long structureId, String referenceActe,
                                    LocalDate dateNomination, LocalDate dateAccreditation) {
    }

    /** Résultat d'une nomination : la fonction créée et les alertes non bloquantes (ex. lien de parenté). */
    public record NominationResultat(FonctionDto fonction, List<String> alertes) {
    }

    public record LienParenteRequest(@NotNull Long parentActeurId, @NotBlank String natureLien) {
    }

    public record MembreDto(Long id, Long acteurId, String nomComplet, RoleCommission roleCommission, Integer rang) {
        public static MembreDto of(CommissionMembre m) {
            return new MembreDto(m.getId(), m.getActeur().getId(), m.getActeur().getNomComplet(), m.getRoleCommission(), m.getRang());
        }
    }

    public record CommissionDto(Long id, Long structureId, String structureCode, Integer exercice, String referenceDecision,
                                LocalDate dateDecision, boolean complete, List<MembreDto> membres) {
        public static CommissionDto of(Commission c, boolean complete) {
            return new CommissionDto(c.getId(), c.getStructure().getId(), c.getStructure().getCode(), c.getExercice().getAnnee(),
                    c.getReferenceDecision(), c.getDateDecision(), complete, c.getMembres().stream().map(MembreDto::of).toList());
        }
    }

    public record MembreRequest(@NotNull Long acteurId, @NotNull RoleCommission roleCommission) {
    }

    public record CommissionRequest(@NotNull Long structureId, @NotNull Integer exercice, String referenceDecision, LocalDate dateDecision,
                                    @NotNull @Size(min = 4, max = 4, message = "la commission comprend exactement 4 membres") List<@jakarta.validation.Valid MembreRequest> membres) {
    }
}
