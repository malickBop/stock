package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.MouvementDtos.LigneRequest;
import sn.masae.gcm.api.dto.MouvementDtos.MouvementRequest;
import sn.masae.gcm.api.dto.OperationDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.workflow.ImpactHandler;
import sn.masae.gcm.api.workflow.WorkflowService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recensement contradictoire : le relevé théorique est figé à l'ouverture, la commission saisit le physique,
 * l'écart (physique − théorique) génère à la validation finale du PV (M20) une entrée pour excédent
 * ou une sortie définitive pour manquant.
 */
@Service
@Transactional
public class RecensementService implements ImpactHandler {

    private final RecensementRepository recensements;
    private final BienRepository biens;
    private final MouvementRepository mouvements;
    private final DocumentReglementaireRepository documents;
    private final ReferentielService referentiel;
    private final CommissionService commissions;
    private final MouvementService mouvementService;
    private final StockService stock;
    private final WorkflowService workflow;
    private final SecuriteService securite;
    private final AuditService audit;

    public RecensementService(RecensementRepository recensements, BienRepository biens, MouvementRepository mouvements,
                              DocumentReglementaireRepository documents, ReferentielService referentiel, CommissionService commissions,
                              MouvementService mouvementService, StockService stock,
                              @org.springframework.context.annotation.Lazy WorkflowService workflow, SecuriteService securite, AuditService audit) {
        this.recensements = recensements;
        this.biens = biens;
        this.mouvements = mouvements;
        this.documents = documents;
        this.referentiel = referentiel;
        this.commissions = commissions;
        this.mouvementService = mouvementService;
        this.stock = stock;
        this.workflow = workflow;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RecensementDto> lister(Long structureId, Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        return recensements.findByStructureIdInAndExerciceIdOrderByDateDebutDesc(securite.perimetre(structureId), e.getId())
                .stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public RecensementDto lire(Long id) {
        return dto(recensement(id));
    }

    public Recensement recensement(Long id) {
        Recensement r = recensements.findById(id).orElseThrow(() -> new NotFoundException("Recensement", id));
        securite.verifierPerimetre(r.getStructure().getId());
        return r;
    }

    public RecensementDto dto(Recensement r) {
        DocumentReglementaire d = documents.findFirstByRecensementIdAndModele(r.getId(), 20).orElse(null);
        return new RecensementDto(r.getId(), r.getNumero(), r.getStructure().getId(), r.getStructure().getCode(), r.getExercice().getAnnee(),
                r.getDateDebut(), r.getDateFin(), r.getStatut(), d == null ? null : d.getId(), d == null ? null : d.getStatut(),
                r.getLignes().stream().map(RecensementLigneDto::of).toList());
    }

    /** Ouverture : génération du relevé théorique à partir de l'existant comptable. */
    public RecensementDto ouvrir(RecensementRequest req) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Structure s = referentiel.structure(req.structureId());
        securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(req.exercice());
        commissions.commissionComplete(s, e);
        Recensement r = new Recensement();
        r.setNumero("REC-" + s.getCode() + "-" + e.getAnnee() + "-" + String.format("%03d", recensements.countByExerciceId(e.getId()) + 1));
        r.setStructure(s);
        r.setExercice(e);
        r.setDateDebut(req.dateDebut());
        r.setObservations(req.observations());
        r.setStatut(StatutOperation.EN_COURS);
        for (Bien b : biens.findByStructureIdOrderByNumeroInventaireAsc(s.getId())) {
            BigDecimal theorique = b.getQteAttente().add(b.getQteService()).add(b.getQteReforme());
            if (theorique.signum() == 0 && b.getQteSortieProvisoire().signum() == 0) continue;
            RecensementLigne l = new RecensementLigne();
            l.setRecensement(r);
            l.setBien(b);
            l.setQteTheorique(theorique);
            l.setPrixUnitaire(b.getPrixUnitaire());
            l.setEtat(b.getEtat());
            r.getLignes().add(l);
        }
        recensements.save(r);
        audit.tracer("OUVERTURE", "Recensement", r.getId(), s.getCode(), r.getNumero() + " (" + r.getLignes().size() + " lignes)");
        return dto(r);
    }

    /** Saisie des quantités physiques constatées par la commission. */
    public RecensementDto saisirComptages(Long id, List<ComptageRequest> comptages) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.MEMBRE_COMMISSION);
        Recensement r = recensement(id);
        if (r.getStatut() != StatutOperation.EN_COURS) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Le recensement n'est plus modifiable");
        }
        for (ComptageRequest c : comptages) {
            RecensementLigne l = r.getLignes().stream().filter(x -> x.getId().equals(c.ligneId())).findFirst()
                    .orElseThrow(() -> new NotFoundException("Ligne de recensement", c.ligneId()));
            l.setQtePhysique(c.qtePhysique());
            if (c.etat() != null) l.setEtat(c.etat());
            l.setObservation(c.observation());
        }
        return dto(r);
    }

    /** Clôture du comptage et ouverture du PV de recensement (M20) dans le circuit. */
    public RecensementDto cloturerComptage(Long id) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Recensement r = recensement(id);
        if (r.getStatut() != StatutOperation.EN_COURS) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Recensement déjà clôturé");
        }
        List<String> nonComptes = r.getLignes().stream().filter(l -> l.getQtePhysique() == null)
                .map(l -> l.getBien().getNumeroInventaire()).toList();
        if (!nonComptes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Tous les biens doivent être comptés", nonComptes);
        }
        r.setDateFin(ReferentielService.aujourdHui());
        r.setStatut(StatutOperation.VALIDE);
        BigDecimal valeur = r.getLignes().stream().map(l -> l.getQtePhysique().multiply(l.getPrixUnitaire())).reduce(BigDecimal.ZERO, BigDecimal::add);
        DocumentReglementaire d = workflow.creer(20, r.getStructure(), r.getExercice(), valeur, true);
        d.setRecensement(r);
        audit.tracer("CLOTURE_COMPTAGE", "Recensement", r.getId(), r.getStructure().getCode(), r.getNumero());
        return dto(r);
    }

    @Override
    public boolean supporte(DocumentReglementaire d) {
        return d.getModele() == 20 && d.getRecensement() != null;
    }

    @Override
    public void appliquer(DocumentReglementaire d) {
        Recensement r = d.getRecensement();
        for (RecensementLigne l : r.getLignes()) {
            BigDecimal ecart = l.getEcart();
            if (ecart.signum() == 0) continue;
            boolean excedent = ecart.signum() > 0;
            MouvementRequest req = new MouvementRequest(r.getStructure().getId(), r.getExercice().getAnnee(),
                    excedent ? TypeMouvement.ENTREE : TypeMouvement.SORTIE_DEFINITIVE,
                    excedent ? SousTypeMouvement.EXCEDENT_INVENTAIRE : SousTypeMouvement.MANQUANT_INVENTAIRE,
                    r.getDateFin(), d.getNumero(), excedent ? null : "Constat de recensement", null, null, null, null, null, null,
                    "Écart de recensement " + r.getNumero(),
                    List.of(new LigneRequest(l.getBien().getId(), null, null, ecart.abs(), null, l.getPrixUnitaire(), l.getEtat(), null, null, null)));
            Mouvement m = mouvementService.construire(req, false);
            mouvements.save(m);
            stock.appliquer(m);
        }
        r.setStatut(StatutOperation.CLOTURE);
    }
}
