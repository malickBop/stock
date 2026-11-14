package sn.masae.gcm.api.dto;

import jakarta.validation.constraints.*;
import sn.masae.gcm.domain.entity.Utilisateur;
import sn.masae.gcm.domain.enums.Role;

import java.time.Instant;
import java.util.Set;

/** DTOs d'authentification et de gestion des comptes. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String motDePasse) {
    }

    public record LoginResponse(String token, Instant expiration, UtilisateurDto utilisateur) {
    }

    public record ChangementMotDePasse(@NotBlank String ancien, @NotBlank @Size(min = 10) String nouveau) {
    }

    public record UtilisateurDto(Long id, String login, String nomComplet, String email, Long structureId, String structureCode,
                                 String structureLibelle, Long acteurId, Set<Role> roles, boolean actif, Instant derniereConnexion) {
        public static UtilisateurDto of(Utilisateur u) {
            return new UtilisateurDto(u.getId(), u.getLogin(), u.getNomComplet(), u.getEmail(), u.getStructure().getId(),
                    u.getStructure().getCode(), u.getStructure().getLibelle(), u.getActeur() == null ? null : u.getActeur().getId(),
                    Set.copyOf(u.getRoles()), u.isActif(), u.getDerniereConnexion());
        }
    }

    public record UtilisateurRequest(@NotBlank @Size(max = 60) String login, @NotBlank String nomComplet, @Email String email,
                                     @NotNull Long structureId, Long acteurId, @NotEmpty Set<Role> roles, Boolean actif,
                                     @Size(min = 10) String motDePasse) {
    }
}
