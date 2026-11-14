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
import sn.masae.gcm.common.util.Montants;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Réforme : proposition du comptable → PV de réforme (M5, commission + visas CBM/CRF et DGB) →
 * biens placés en « réformé » → PV de vente/destruction (M6) → sortie définitive.
 */
@Service
@Transactional
public class ReformeService implements ImpactHandler {

    private final ReformeRepository reformes;
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

    public ReformeService(ReformeRepository reformes, BienRepository biens, MouvementRepository mouvements,
                          DocumentReglementaireRepository documents, ReferentielService referentiel, CommissionService commissions,
                          MouvementService mouvementService, StockService stock,
                          @org.springframework.context.annotation.Lazy WorkflowService workflow, SecuriteService securite, AuditService audit) {
        this.reformes = reformes;
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
    public List<ReformeDto> lister(Long structureId, Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        return reformes.findByStructureIdInAndExerciceIdOrderByDatePropositionDesc(securite.perimetre(structureId), e.getId())
                .stream().map(this::dto).toList();
    }

    public Reforme reforme(Long id) {
        Reforme r = reformes.findById(id).orElseThrow(() -> new NotFoundException("Réforme", id));
        securite.verifierPerimetre(r.getStructure().getId());
        return r;
    }

    @Transactional(readOnly = true)
    public ReformeDto lire(Long id) {
        return dto(reforme(id));
    }

    public ReformeDto dto(Reforme r) {
        Long m5 = documents.findFirstByReformeIdAndModele(r.getId(), 5).map(DocumentReglementaire::getId).orElse(null);
        Long m6 = documents.findFirstByReformeIdAndModele(r.getId(), 6).map(DocumentReglementaire::getId).orElse(null);
        return new ReformeDto(r.getId(), r.getNumero(), r.getStructure().getCode(), r.getExercice().getAnnee(), r.getDateProposition(),
                r.getMotif(), r.getStatut(), r.getDecision(), r.getDateExecution(), r.getProduitVente(), m5, m6,
                r.getLignes().stream().map(ReformeLigneDto::of).toList());
    }

    /** Proposition de réforme par le comptable ; ouvre le PV de réforme (M5). */
    public ReformeDto proposer(ReformeRequest req) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Structure s = referentiel.structure(req.structureId());
        securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(req.exercice());
        commissions.commissionComplete(s, e);
        Reforme r = new Reforme();
        r.setNumero("REF-" + s.getCode() + "-" + e.getAnnee() + "-" + String.format("%03d", reformes.countByExerciceId(e.getId()) + 1));
        r.setStructure(s);
        r.setExercice(e);
        r.setDateProposition(req.dateProposition());
        r.setMotif(req.motif());
        r.setStatut(StatutOperation.EN_COURS);
        BigDecimal total = BigDecimal.ZERO;
        for (ReformeLigneRequest lr : req.lignes()) {
            Bien b = biens.findById(lr.bienId()).orElseThrow(() -> new NotFoundException("Bien", lr.bienId()));
            if (!b.getStructure().getId().equals(s.getId())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Le bien " + b.getNumeroInventaire() + " n'appartient pas à " + s.getCode());
            }
            BigDecimal dispo = b.getQteAttente().add(b.getQteService());
            if (dispo.compareTo(lr.quantite()) < 0) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFISANT, "Quantité proposée supérieure à l'existant disponible pour "
                        + b.getNumeroInventaire() + " (" + Montants.quantite(dispo) + ")");
            }
            ReformeLigne l = new ReformeLigne();
            l.setReforme(r);
            l.setBien(b);
            l.setQuantite(lr.quantite());
            l.setValeurUnitaire(b.getPrixUnitaire());
            l.setEtat(lr.etat() == null ? b.getEtat() : lr.etat());
            l.setMotif(lr.motif());
            r.getLignes().add(l);
            total = total.add(Montants.montant(lr.quantite(), b.getPrixUnitaire()));
        }
        reformes.save(r);
        DocumentReglementaire d = workflow.creer(5, s, e, total, true);
        d.setReforme(r);
        audit.tracer("PROPOSITION", "Reforme", r.getId(), s.getCode(), r.getNumero());
        return dto(r);
    }

    /** Après réforme prononcée : décision de vente/destruction, ouvre le PV M6. */
    public ReformeDto executer(Long id, ExecutionReformeRequest req) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Reforme r = reforme(id);
        if (r.getStatut() != StatutOperation.VALIDE) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Le PV de réforme doit être validé (visas CBM/CRF et DGB) avant exécution");
        }
        if (req.decision() == DecisionReforme.VENTE && (req.produitVente() == null || req.referenceQuittance() == null)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Une vente exige le produit de la vente et la référence de la quittance du Trésor");
        }
        r.setDecision(req.decision());
        r.setDateExecution(req.dateExecution());
        r.setProduitVente(req.produitVente());
        r.setReferenceQuittance(req.referenceQuittance());
        BigDecimal total = r.getLignes().stream().map(l -> Montants.montant(l.getQuantite(), l.getValeurUnitaire())).reduce(BigDecimal.ZERO, BigDecimal::add);
        DocumentReglementaire d = workflow.creer(6, r.getStructure(), r.getExercice(), total, true);
        d.setReforme(r);
        audit.tracer("EXECUTION", "Reforme", r.getId(), r.getStructure().getCode(), req.decision().name());
        return dto(r);
    }

    @Override
    public boolean supporte(DocumentReglementaire d) {
        return d.getReforme() != null && (d.getModele() == 5 || d.getModele() == 6);
    }

    @Override
    public void appliquer(DocumentReglementaire d) {
        Reforme r = d.getReforme();
        if (d.getModele() == 5) {
            for (ReformeLigne l : r.getLignes()) {
                Bien b = l.getBien();
                BigDecimal q = l.getQuantite();
                BigDecimal deAttente = b.getQteAttente().min(q);
                BigDecimal deService = q.subtract(deAttente);
                if (b.getQteService().compareTo(deService) < 0) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFISANT, "Existant insuffisant pour réformer " + b.getNumeroInventaire());
                }
                b.setQteAttente(b.getQteAttente().subtract(deAttente));
                b.setQteService(b.getQteService().subtract(deService));
                b.setQteReforme(b.getQteReforme().add(q));
                b.setEtat(EtatBien.HORS_USAGE);
            }
            r.setStatut(StatutOperation.VALIDE);
            return;
        }
        SousTypeMouvement st = switch (r.getDecision()) {
            case VENTE -> SousTypeMouvement.VENTE;
            case DESTRUCTION -> SousTypeMouvement.DESTRUCTION;
            case CESSION -> SousTypeMouvement.CESSION;
        };
        List<LigneRequest> lignes = r.getLignes().stream()
                .map(l -> new LigneRequest(l.getBien().getId(), null, null, l.getQuantite(), null, null, l.getEtat(), null, null, null)).toList();
        MouvementRequest req = new MouvementRequest(r.getStructure().getId(), r.getExercice().getAnnee(), TypeMouvement.SORTIE_DEFINITIVE, st,
                r.getDateExecution(), d.getNumero(), r.getDecision() == DecisionReforme.VENTE ? "Adjudicataire (quittance " + r.getReferenceQuittance() + ")" : "Destruction",
                null, null, null, null, null, null, "Exécution de la réforme " + r.getNumero(), lignes);
        Mouvement m = mouvementService.construire(req, false);
        mouvements.save(m);
        stock.appliquer(m);
        r.setStatut(StatutOperation.CLOTURE);
    }
}
