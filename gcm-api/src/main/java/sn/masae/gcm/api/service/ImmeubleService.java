package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.ImmeubleDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.Immeuble;
import sn.masae.gcm.domain.entity.Structure;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.enums.TypeImmeuble;
import sn.masae.gcm.domain.repository.ImmeubleRepository;

import java.util.List;

/** Gestion du patrimoine immobilier et des baux. */
@Service
@Transactional
public class ImmeubleService {

    private final ImmeubleRepository immeubles;
    private final ReferentielService referentiel;
    private final SecuriteService securite;
    private final AuditService audit;

    public ImmeubleService(ImmeubleRepository immeubles, ReferentielService referentiel, SecuriteService securite, AuditService audit) {
        this.immeubles = immeubles;
        this.referentiel = referentiel;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ImmeubleDto> lister(Long structureId, TypeImmeuble type) {
        var ids = securite.perimetre(structureId);
        return (type == null ? immeubles.findByStructureIdInOrderByReferenceAsc(ids)
                : immeubles.findByStructureIdInAndTypeImmeubleOrderByReferenceAsc(ids, type)).stream().map(ImmeubleDto::of).toList();
    }

    public ImmeubleDto enregistrer(Long id, ImmeubleRequest r) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Structure s = referentiel.structure(r.structureId());
        securite.verifierPerimetre(s.getId());
        if (r.typeImmeuble() == TypeImmeuble.BAIL && (r.bailleur() == null || r.loyerMensuel() == null || r.dateDebutBail() == null)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Un bail exige le bailleur, le loyer mensuel et la date de début");
        }
        if (r.dateFinBail() != null && r.dateDebutBail() != null && r.dateFinBail().isBefore(r.dateDebutBail())) {
            throw new BusinessException(ErrorCode.VALIDATION, "La fin du bail précède son début");
        }
        Immeuble i = id == null ? new Immeuble() : immeubles.findById(id).orElseThrow(() -> new NotFoundException("Immeuble", id));
        if (id == null) {
            String prefix = switch (r.typeImmeuble()) {
                case BATI -> "PB";
                case NON_BATI -> "PNB";
                case BAIL -> "BL";
            };
            i.setReference(prefix + "-" + s.getCode() + "-" + String.format("%04d", immeubles.count() + 1));
        } else {
            securite.verifierPerimetre(i.getStructure().getId());
        }
        i.setStructure(s);
        i.setTypeImmeuble(r.typeImmeuble());
        i.setCompte(r.compteId() == null ? null : referentiel.compte(r.compteId()));
        i.setDesignation(r.designation());
        i.setLocalisation(r.localisation());
        i.setTitreFoncier(r.titreFoncier());
        i.setSuperficie(r.superficie());
        i.setSuperficieBatie(r.superficieBatie());
        i.setNombrePieces(r.nombrePieces());
        i.setValeur(r.valeur());
        i.setDateAcquisition(r.dateAcquisition());
        i.setModeAcquisition(r.modeAcquisition());
        i.setAffectation(r.affectation());
        i.setEtat(r.etat());
        i.setBailleur(r.bailleur());
        i.setLoyerMensuel(r.loyerMensuel());
        i.setDateDebutBail(r.dateDebutBail());
        i.setDateFinBail(r.dateFinBail());
        i.setObservations(r.observations());
        immeubles.save(i);
        audit.tracer(id == null ? "CREATION" : "MODIFICATION", "Immeuble", i.getId(), s.getCode(), i.getReference());
        return ImmeubleDto.of(i);
    }
}
