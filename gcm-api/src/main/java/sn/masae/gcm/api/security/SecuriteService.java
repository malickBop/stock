package sn.masae.gcm.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.common.exception.AccessDeniedBusinessException;
import sn.masae.gcm.domain.entity.Structure;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.repository.StructureRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôle d'accès RBAC et périmètre hiérarchique : un utilisateur rattaché à une structure
 * voit sa structure et toutes ses structures descendantes ; les rôles nationaux voient tout.
 */
@Service
@Transactional(readOnly = true)
public class SecuriteService {

    private final StructureRepository structures;

    public SecuriteService(StructureRepository structures) {
        this.structures = structures;
    }

    public UtilisateurCourant courant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedBusinessException("Utilisateur non authentifié");
        }
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());
        return new UtilisateurCourant(toLong(jwt.getClaim("uid")), jwt.getSubject(), jwt.getClaimAsString("nom"),
                toLong(jwt.getClaim("structureId")), toLong(jwt.getClaim("acteurId")),
                roles.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet()));
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }

    /** Identifiants des structures accessibles par l'utilisateur courant. */
    public Set<Long> perimetre() {
        return perimetre(courant());
    }

    public Set<Long> perimetre(UtilisateurCourant u) {
        List<Structure> toutes = structures.findAll();
        if (u.perimetreGlobal()) {
            return toutes.stream().map(Structure::getId).collect(Collectors.toSet());
        }
        Map<Long, List<Long>> enfants = new HashMap<>();
        for (Structure s : toutes) {
            if (s.getParent() != null) {
                enfants.computeIfAbsent(s.getParent().getId(), k -> new ArrayList<>()).add(s.getId());
            }
        }
        Set<Long> res = new LinkedHashSet<>();
        Deque<Long> pile = new ArrayDeque<>(List.of(u.structureId()));
        while (!pile.isEmpty()) {
            Long id = pile.pop();
            if (res.add(id)) {
                pile.addAll(enfants.getOrDefault(id, List.of()));
            }
        }
        return res;
    }

    /** Restreint éventuellement à une structure choisie (filtre), en vérifiant qu'elle est dans le périmètre. */
    public Set<Long> perimetre(Long structureFiltre) {
        Set<Long> p = perimetre();
        if (structureFiltre == null) return p;
        if (!p.contains(structureFiltre)) {
            throw new AccessDeniedBusinessException("Structure hors de votre périmètre");
        }
        return Set.of(structureFiltre);
    }

    public void verifierPerimetre(Long structureId) {
        if (!perimetre().contains(structureId)) {
            throw new AccessDeniedBusinessException("Opération refusée : structure hors de votre périmètre hiérarchique");
        }
    }

    public UtilisateurCourant exiger(Role... roles) {
        UtilisateurCourant u = courant();
        if (u.a(Role.ADMIN) || u.a(roles)) return u;
        throw new AccessDeniedBusinessException("Rôle insuffisant : " + Arrays.toString(roles) + " requis");
    }
}
