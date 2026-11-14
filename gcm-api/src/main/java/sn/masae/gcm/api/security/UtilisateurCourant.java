package sn.masae.gcm.api.security;

import sn.masae.gcm.domain.enums.Role;

import java.util.Set;

/**
 * Identité de l'utilisateur connecté, extraite du jeton.
 */
public record UtilisateurCourant(Long id, String login, String nomComplet, Long structureId, Long acteurId, Set<Role> roles) {

    public boolean a(Role... rs) {
        for (Role r : rs) {
            if (roles.contains(r)) return true;
        }
        return false;
    }

    /** Rôles à périmètre national (toutes structures). */
    public boolean perimetreGlobal() {
        return a(Role.ADMIN, Role.ORDONNATEUR_PRINCIPAL, Role.COMPTABLE_ORDRE, Role.CONTROLE) || structureId == null;
    }

    public boolean estOrdonnateur() {
        return a(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
    }

    public boolean estComptable() {
        return a(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE);
    }
}
