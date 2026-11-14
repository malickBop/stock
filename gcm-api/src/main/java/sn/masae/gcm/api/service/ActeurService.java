package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.ReferentielDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.common.exception.AccessDeniedBusinessException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.FonctionActeur;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.repository.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Gestion des acteurs, de leurs nominations et des liens de parenté déclarés.
 */
@Service
@Transactional
public class ActeurService {

    private final ActeurRepository acteurs;
    private final ActeurFonctionRepository fonctions;
    private final LienParenteRepository liens;
    private final ReferentielService referentiel;
    private final IncompatibiliteValidator validator;
    private final SecuriteService securite;
    private final AuditService audit;

    public ActeurService(ActeurRepository acteurs, ActeurFonctionRepository fonctions, LienParenteRepository liens,
                         ReferentielService referentiel, IncompatibiliteValidator validator, SecuriteService securite, AuditService audit) {
        this.acteurs = acteurs;
        this.fonctions = fonctions;
        this.liens = liens;
        this.referentiel = referentiel;
        this.validator = validator;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ActeurDto> lister(Long structureId) {
        Set<Long> p = securite.perimetre(structureId);
        Map<Long, List<ActeurFonction>> parActeur = new HashMap<>();
        for (Long s : p) {
            for (ActeurFonction f : fonctions.findByStructureIdAndActifTrue(s)) {
                parActeur.computeIfAbsent(f.getActeur().getId(), k -> new ArrayList<>());
            }
        }
        List<Acteur> liste = structureId == null && securite.courant().perimetreGlobal() ? acteurs.findAll()
                : acteurs.findAllById(parActeur.keySet());
        return liste.stream().sorted(Comparator.comparing(Acteur::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(a -> ActeurDto.of(a, fonctions.findByActeurIdAndActifTrue(a.getId()))).toList();
    }

    public Acteur acteur(Long id) {
        return acteurs.findById(id).orElseThrow(() -> new NotFoundException("Acteur", id));
    }

    public ActeurDto creer(ActeurRequest r) {
        securite.exiger(Role.ADMIN, Role.COMPTABLE_PRINCIPAL, Role.ORDONNATEUR_DELEGUE);
        if (r.matricule() != null && !r.matricule().isBlank()) {
            acteurs.findByMatricule(r.matricule()).ifPresent(a -> {
                throw new BusinessException(ErrorCode.CONFLIT, "Matricule déjà enregistré : " + r.matricule());
            });
        }
        Acteur a = new Acteur();
        appliquer(a, r);
        acteurs.save(a);
        audit.tracer("CREATION", "Acteur", a.getId(), null, a.getNomComplet());
        return ActeurDto.of(a, List.of());
    }

    public ActeurDto modifier(Long id, ActeurRequest r) {
        securite.exiger(Role.ADMIN, Role.COMPTABLE_PRINCIPAL, Role.ORDONNATEUR_DELEGUE);
        Acteur a = acteur(id);
        if (!securite.courant().perimetreGlobal()) {
            Set<Long> p = securite.perimetre();
            if (fonctions.findByActeurIdAndActifTrue(id).stream().anyMatch(f -> !p.contains(f.getStructure().getId()))) {
                throw new AccessDeniedBusinessException("Acteur nommé dans une structure hors de votre périmètre");
            }
        }
        appliquer(a, r);
        audit.tracer("MODIFICATION", "Acteur", a.getId(), null, a.getNomComplet());
        return ActeurDto.of(a, fonctions.findByActeurIdAndActifTrue(id));
    }

    private void appliquer(Acteur a, ActeurRequest r) {
        a.setMatricule(r.matricule() == null || r.matricule().isBlank() ? null : r.matricule().trim());
        a.setNom(r.nom().trim());
        a.setPrenom(r.prenom());
        a.setTitre(r.titre());
        a.setEmail(r.email());
        a.setTelephone(r.telephone());
    }

    /** Nomme un acteur à une fonction après contrôle bloquant des incompatibilités. */
    public NominationResultat nommer(Long acteurId, NominationRequest r) {
        securite.exiger(Role.ADMIN, Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE);
        Acteur a = acteur(acteurId);
        Structure s = referentiel.structure(r.structureId());
        securite.verifierPerimetre(s.getId());
        verifierUnicite(r.fonction(), s);
        List<String> alertes = validator.verifierNomination(a, r.fonction(), s);
        ActeurFonction f = new ActeurFonction();
        f.setActeur(a);
        f.setStructure(s);
        f.setFonction(r.fonction());
        f.setReferenceActe(r.referenceActe());
        f.setDateNomination(r.dateNomination());
        f.setDateAccreditation(r.dateAccreditation());
        fonctions.save(f);
        audit.tracer("NOMINATION", "Acteur", a.getId(), s.getCode(), r.fonction() + (alertes.isEmpty() ? "" : " ALERTES=" + alertes));
        return new NominationResultat(FonctionDto.of(f), alertes);
    }

    private void verifierUnicite(FonctionActeur fonction, Structure s) {
        boolean comptable = fonction == FonctionActeur.COMPTABLE_PRINCIPAL || fonction == FonctionActeur.COMPTABLE_SECONDAIRE;
        if (comptable && (!fonctions.findByStructureIdAndFonctionAndActifTrue(s.getId(), FonctionActeur.COMPTABLE_PRINCIPAL).isEmpty()
                || !fonctions.findByStructureIdAndFonctionAndActifTrue(s.getId(), FonctionActeur.COMPTABLE_SECONDAIRE).isEmpty())) {
            throw new BusinessException("La structure " + s.getCode() + " dispose déjà d'un comptable des matières actif (art. 30)");
        }
        if (fonction == FonctionActeur.ORDONNATEUR_PRINCIPAL && !fonctions.findByFonctionAndActifTrue(fonction).isEmpty()) {
            throw new BusinessException("Un ordonnateur principal est déjà en fonction");
        }
    }

    public void mettreFin(Long fonctionId) {
        securite.exiger(Role.ADMIN, Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE);
        ActeurFonction f = fonctions.findById(fonctionId).orElseThrow(() -> new NotFoundException("Fonction", fonctionId));
        securite.verifierPerimetre(f.getStructure().getId());
        f.setActif(false);
        f.setDateFin(LocalDate.now());
        audit.tracer("FIN_FONCTION", "Acteur", f.getActeur().getId(), f.getStructure().getCode(), f.getFonction().name());
    }

    public void declarerLien(Long acteurId, LienParenteRequest r) {
        securite.exiger(Role.ADMIN, Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.COMPTABLE_PRINCIPAL);
        if (acteurId.equals(r.parentActeurId())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Un acteur ne peut être lié à lui-même");
        }
        LienParente l = new LienParente();
        l.setActeur(acteur(acteurId));
        l.setParentActeur(acteur(r.parentActeurId()));
        l.setNatureLien(r.natureLien());
        liens.save(l);
        audit.tracer("DECLARATION_LIEN", "Acteur", acteurId, null, r.natureLien() + " avec acteur " + r.parentActeurId());
    }

    /** Acteur titulaire actif d'une fonction dans une structure (ou ses ancêtres pour les ordonnateurs). */
    @Transactional(readOnly = true)
    public Optional<Acteur> titulaire(Structure s, FonctionActeur... fs) {
        for (Structure c = s; c != null; c = c.getParent()) {
            for (FonctionActeur f : fs) {
                List<ActeurFonction> l = fonctions.findByStructureIdAndFonctionAndActifTrue(c.getId(), f);
                if (!l.isEmpty()) return Optional.of(l.get(0).getActeur());
            }
        }
        return Optional.empty();
    }
}
