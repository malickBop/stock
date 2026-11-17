package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.MouvementDtos.MouvementRequest;
import sn.masae.gcm.api.dto.OperationDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.workflow.ImpactHandler;
import sn.masae.gcm.api.workflow.WorkflowService;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.util.List;

/**
 * Certificat administratif (M21) : autorise la sortie de matières pour perte, vol, destruction ou consommation ;
 * le visa du contrôleur (CBM/CRF) est requis avant validation, qui comptabilise la sortie définitive.
 */
@Service
@Transactional
public class CertificatService implements ImpactHandler {

    private final CertificatAdministratifRepository certificats;
    private final MouvementRepository mouvements;
    private final DocumentReglementaireRepository documents;
    private final ReferentielService referentiel;
    private final MouvementService mouvementService;
    private final StockService stock;
    private final WorkflowService workflow;
    private final SecuriteService securite;
    private final AuditService audit;

    public CertificatService(CertificatAdministratifRepository certificats, MouvementRepository mouvements,
                             DocumentReglementaireRepository documents, ReferentielService referentiel, MouvementService mouvementService,
                             StockService stock, @org.springframework.context.annotation.Lazy WorkflowService workflow,
                             SecuriteService securite, AuditService audit) {
        this.certificats = certificats;
        this.mouvements = mouvements;
        this.documents = documents;
        this.referentiel = referentiel;
        this.mouvementService = mouvementService;
        this.stock = stock;
        this.workflow = workflow;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CertificatDto> lister(Long structureId, Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        return certificats.findByStructureIdInAndExerciceIdOrderByDateCertificatDesc(securite.perimetre(structureId), e.getId())
                .stream().map(this::dto).toList();
    }

    public CertificatDto dto(CertificatAdministratif c) {
        Long doc = documents.findFirstByCertificatIdAndModele(c.getId(), 21).map(DocumentReglementaire::getId).orElse(null);
        return new CertificatDto(c.getId(), c.getNumero(), c.getStructure().getCode(), c.getExercice().getAnnee(), c.getObjet(), c.getMotif(),
                c.getDateCertificat(), c.getStatut(), c.getMouvement() == null ? null : c.getMouvement().getId(), doc,
                c.getMouvement() == null ? null : c.getMouvement().getMontantTotal());
    }

    public CertificatDto creer(CertificatRequest req) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE,
                Role.ORDONNATEUR_SECONDAIRE);
        Structure s = referentiel.structure(req.structureId());
        securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(req.exercice());
        SousTypeMouvement st = switch (req.objet()) {
            case PERTE -> SousTypeMouvement.PERTE;
            case VOL -> SousTypeMouvement.VOL;
            case DESTRUCTION -> SousTypeMouvement.DESTRUCTION;
            case CONSOMMATION -> SousTypeMouvement.CONSOMMATION;
            case REGULARISATION -> SousTypeMouvement.MANQUANT_INVENTAIRE;
        };
        CertificatAdministratif c = new CertificatAdministratif();
        c.setNumero("CA-" + s.getCode() + "-" + e.getAnnee() + "-" + String.format("%03d", certificats.countByExerciceId(e.getId()) + 1));
        MouvementRequest mr = new MouvementRequest(s.getId(), e.getAnnee(), TypeMouvement.SORTIE_DEFINITIVE,
                st == SousTypeMouvement.DESTRUCTION ? SousTypeMouvement.PERTE : st, req.dateCertificat(), c.getNumero(),
                "Certificat administratif", null, null, null, null, null, null, req.motif(), req.lignes());
        Mouvement m = mouvementService.construire(mr, true);
        m.setStatut(StatutMouvement.EN_COURS);
        mouvements.save(m);
        c.setStructure(s);
        c.setExercice(e);
        c.setObjet(req.objet());
        c.setMotif(req.motif());
        c.setDateCertificat(req.dateCertificat());
        c.setStatut(StatutOperation.EN_COURS);
        c.setMouvement(m);
        certificats.save(c);
        DocumentReglementaire d = workflow.creer(21, s, e, m.getMontantTotal(), false);
        d.setCertificat(c);
        audit.tracer("CREATION", "Certificat", c.getId(), s.getCode(), c.getNumero() + " " + req.objet());
        return dto(c);
    }

    @Override
    public boolean supporte(DocumentReglementaire d) {
        return d.getModele() == 21 && d.getCertificat() != null;
    }

    @Override
    public void appliquer(DocumentReglementaire d) {
        CertificatAdministratif c = d.getCertificat();
        stock.appliquer(c.getMouvement());
        c.setStatut(StatutOperation.VALIDE);
    }

    public CertificatAdministratif certificat(Long id) {
        CertificatAdministratif c = certificats.findById(id).orElseThrow(() -> new NotFoundException("Certificat", id));
        securite.verifierPerimetre(c.getStructure().getId());
        return c;
    }
}
