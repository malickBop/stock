package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.ReferentielDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.enums.RoleCommission;
import sn.masae.gcm.domain.repository.CommissionRepository;

import java.util.*;

/**
 * Commissions annuelles par structure : un Président, un Suppléant et deux Membres (instructions, § acteurs).
 */
@Service
@Transactional
public class CommissionService {

    private final CommissionRepository commissions;
    private final ReferentielService referentiel;
    private final ActeurService acteurs;
    private final IncompatibiliteValidator validator;
    private final SecuriteService securite;
    private final AuditService audit;

    public CommissionService(CommissionRepository commissions, ReferentielService referentiel, ActeurService acteurs,
                             IncompatibiliteValidator validator, SecuriteService securite, AuditService audit) {
        this.commissions = commissions;
        this.referentiel = referentiel;
        this.acteurs = acteurs;
        this.validator = validator;
        this.securite = securite;
        this.audit = audit;
    }

    /** Vérifie la composition : exactement 1 Président, 1 Suppléant, 2 Membres, sans doublon. */
    public static List<String> anomaliesComposition(Collection<RoleCommission> roles, Collection<Long> acteurIds) {
        List<String> a = new ArrayList<>();
        long pres = roles.stream().filter(r -> r == RoleCommission.PRESIDENT).count();
        long sup = roles.stream().filter(r -> r == RoleCommission.SUPPLEANT).count();
        long mem = roles.stream().filter(r -> r == RoleCommission.MEMBRE).count();
        if (pres != 1) a.add("Un Président est requis (trouvé : " + pres + ")");
        if (sup != 1) a.add("Un Suppléant est requis (trouvé : " + sup + ")");
        if (mem != 2) a.add("Deux Membres sont requis (trouvé : " + mem + ")");
        if (new HashSet<>(acteurIds).size() != acteurIds.size()) a.add("Un même acteur ne peut occuper deux positions");
        return a;
    }

    public static boolean complete(Commission c) {
        return anomaliesComposition(c.getMembres().stream().map(CommissionMembre::getRoleCommission).toList(),
                c.getMembres().stream().map(m -> m.getActeur().getId()).toList()).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<CommissionDto> lister(Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        Set<Long> p = securite.perimetre();
        return commissions.findByExerciceId(e.getId()).stream().filter(c -> p.contains(c.getStructure().getId()))
                .map(c -> CommissionDto.of(c, complete(c))).toList();
    }

    public CommissionDto enregistrer(CommissionRequest r) {
        securite.exiger(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
        Structure s = referentiel.structure(r.structureId());
        securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(r.exercice());
        List<String> anomalies = anomaliesComposition(r.membres().stream().map(MembreRequest::roleCommission).toList(),
                r.membres().stream().map(MembreRequest::acteurId).toList());
        if (!anomalies.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMISSION_INCOMPLETE, "Composition de la commission invalide", anomalies);
        }
        Commission c = commissions.findByStructureIdAndExerciceId(s.getId(), e.getId()).orElseGet(Commission::new);
        c.setStructure(s);
        c.setExercice(e);
        c.setReferenceDecision(r.referenceDecision());
        c.setDateDecision(r.dateDecision());
        c.getMembres().clear();
        if (c.getId() != null) {
            commissions.saveAndFlush(c);
        }
        int rang = 1;
        List<MembreRequest> ordonnes = new ArrayList<>(r.membres());
        ordonnes.sort(Comparator.comparing(MembreRequest::roleCommission));
        for (MembreRequest m : ordonnes) {
            Acteur a = acteurs.acteur(m.acteurId());
            validator.verifierMembreCommission(a, s);
            CommissionMembre cm = new CommissionMembre();
            cm.setCommission(c);
            cm.setActeur(a);
            cm.setRoleCommission(m.roleCommission());
            cm.setRang(rang++);
            c.getMembres().add(cm);
        }
        commissions.save(c);
        audit.tracer("COMMISSION", "Commission", c.getId(), s.getCode(), "Exercice " + e.getAnnee());
        return CommissionDto.of(c, true);
    }

    /** Commission complète exigée (réception au-delà du seuil, réforme, recensement). */
    @Transactional(readOnly = true)
    public Commission commissionComplete(Structure s, Exercice e) {
        Commission c = commissions.findByStructureIdAndExerciceId(s.getId(), e.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMISSION_INCOMPLETE,
                        "Aucune commission n'est désignée pour " + s.getCode() + " au titre de l'exercice " + e.getAnnee()));
        if (!complete(c)) {
            throw new BusinessException(ErrorCode.COMMISSION_INCOMPLETE, "La commission de " + s.getCode() + " est incomplète",
                    anomaliesComposition(c.getMembres().stream().map(CommissionMembre::getRoleCommission).toList(),
                            c.getMembres().stream().map(m -> m.getActeur().getId()).toList()));
        }
        return c;
    }

    @Transactional(readOnly = true)
    public Optional<Commission> commission(Structure s, Exercice e) {
        return commissions.findByStructureIdAndExerciceId(s.getId(), e.getId());
    }
}
