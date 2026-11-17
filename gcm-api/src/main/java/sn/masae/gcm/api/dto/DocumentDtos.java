package sn.masae.gcm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTOs des documents réglementaires et du circuit hybride. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record SignatureDto(Long id, RoleSignature roleSignature, String signataireNom, Instant dateSignature) {
        public static SignatureDto of(DocumentSignature s) {
            return new SignatureDto(s.getId(), s.getRoleSignature(), s.getSignataireNom(), s.getDateSignature());
        }
    }

    public record VisaDto(Long id, TypeVisa typeVisa, String autorite, String referenceVisa, LocalDate dateVisa, Long pieceJointeId) {
        public static VisaDto of(DocumentVisaExterne v) {
            return new VisaDto(v.getId(), v.getTypeVisa(), v.getAutorite(), v.getReferenceVisa(), v.getDateVisa(),
                    v.getPieceJointe() == null ? null : v.getPieceJointe().getId());
        }
    }

    public record PieceDto(Long id, TypePieceJointe typePiece, String nomFichier, String contentType, Long taille, String sha256,
                           Instant dateDepot, String deposePar) {
        public static PieceDto of(PieceJointe p) {
            return new PieceDto(p.getId(), p.getTypePiece(), p.getNomFichier(), p.getContentType(), p.getTaille(), p.getSha256(),
                    p.getCreatedAt(), p.getCreatedBy());
        }
    }

    public record HistoriqueDto(StatutWorkflow ancienStatut, StatutWorkflow nouveauStatut, String action, String utilisateur,
                                String commentaire, Instant date) {
        public static HistoriqueDto of(DocumentHistorique h) {
            return new HistoriqueDto(h.getAncienStatut(), h.getNouveauStatut(), h.getAction(), h.getUtilisateurLogin(), h.getCommentaire(),
                    h.getDateAction());
        }
    }

    /** Exigence restant à satisfaire pour faire progresser le document. */
    public record Exigence(String libelle, boolean satisfaite) {
    }

    public record DocumentDto(Long id, Integer modele, String numero, String titre, Long structureId, String structureCode, Integer exercice,
                              StatutWorkflow statut, boolean requiertCommission, BigDecimal montantTotal, Integer numeroVersion,
                              Long mouvementId, Long recensementId, Long reformeId, Long certificatId, Instant dateCreation,
                              String auteur, Instant dateValidationFinale, String validePar, String motifRejet,
                              List<SignatureDto> signatures, List<VisaDto> visas, List<PieceDto> pieces, List<Exigence> exigences,
                              List<String> actionsPossibles) {
    }

    public record DocumentResume(Long id, Integer modele, String numero, String titre, String structureCode, StatutWorkflow statut,
                                 BigDecimal montantTotal, Instant dateCreation, String auteur) {
        public static DocumentResume of(DocumentReglementaire d) {
            return new DocumentResume(d.getId(), d.getModele(), d.getNumero(), d.getTitre(), d.getStructure().getCode(), d.getStatut(),
                    d.getMontantTotal(), d.getCreatedAt(), d.getCreatedBy());
        }
    }

    public record SignatureRequest(@NotNull RoleSignature roleSignature, Long acteurId, String commentaire) {
    }

    public record RejetRequest(@NotBlank String motif) {
    }

    public record GenerationRequest(@NotNull Integer modele, @NotNull Long structureId, Integer exercice, Long compteId, Long bienId,
                                    Long acteurId) {
    }
}
