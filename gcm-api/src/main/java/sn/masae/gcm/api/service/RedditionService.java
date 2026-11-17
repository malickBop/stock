package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.EtatDtos.Balance;
import sn.masae.gcm.api.dto.EtatDtos.RedditionDto;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.workflow.ImpactHandler;
import sn.masae.gcm.api.workflow.WorkflowService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.util.Echeances;
import sn.masae.gcm.common.util.Montants;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Reddition des comptes (échéance 30 juin N+1) : compte de gestion par comptable (M24), bordereau de
 * centralisation (M23), compte central des matières (M25) et compte administratif (M26) ; clôture d'exercice.
 */
@Service
@Transactional
public class RedditionService implements ImpactHandler {

    private static final Set<StatutWorkflow> EN_CIRCUIT = EnumSet.of(StatutWorkflow.BROUILLON, StatutWorkflow.SIGNE_COMMISSION,
            StatutWorkflow.EN_ATTENTE_VISA_EXTERNE, StatutWorkflow.SCAN_IMPORTE);

    private final RedditionRepository redditions;
    private final DocumentReglementaireRepository documents;
    private final StructureRepository structures;
    private final ExerciceRepository exercices;
    private final BienRepository biens;
    private final SoldeOuvertureRepository soldes;
    private final EcritureJournalRepository journal;
    private final ReferentielService referentiel;
    private final EtatService etats;
    private final WorkflowService workflow;
    private final SecuriteService securite;
    private final AuditService audit;

    public RedditionService(RedditionRepository redditions, DocumentReglementaireRepository documents, StructureRepository structures,
                            ExerciceRepository exercices, BienRepository biens, SoldeOuvertureRepository soldes, EcritureJournalRepository journal, ReferentielService referentiel,
                            EtatService etats, @org.springframework.context.annotation.Lazy WorkflowService workflow, SecuriteService securite,
                            AuditService audit) {
        this.redditions = redditions;
        this.documents = documents;
        this.structures = structures;
        this.exercices = exercices;
        this.biens = biens;
        this.soldes = soldes;
        this.journal = journal;
        this.referentiel = referentiel;
        this.etats = etats;
        this.workflow = workflow;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RedditionDto> lister(Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        Set<Long> p = securite.perimetre();
        return redditions.findByExerciceId(e.getId()).stream().filter(r -> p.contains(r.getStructure().getId())).map(this::dto).toList();
    }

    public RedditionDto dto(Reddition r) {
        int an = r.getExercice().getAnnee();
        return new RedditionDto(r.getId(), r.getStructure().getCode(), an, r.getTypeReddition(), r.getStatut(), r.getDateDepot(),
                r.getDocument() == null ? null : r.getDocument().getId(), Echeances.echeanceReddition(an),
                Echeances.statut(an, ReferentielService.aujourdHui()).name());
    }

    /** Préparation d'un compte : contrôle d'absence de pièce en circuit et d'équilibre de la balance. */
    public RedditionDto preparer(Long structureId, Integer annee, TypeReddition type) {
        Structure s = referentiel.structure(structureId);
        securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(annee);
        int modele;
        Set<Long> ids;
        switch (type) {
            case COMPTE_GESTION -> {
                securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
                modele = 24;
                ids = Set.of(s.getId());
            }
            case BORDEREAU_CENTRALISATION -> {
                securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_ORDRE);
                modele = 23;
                ids = securite.perimetre(s.getId());
            }
            case COMPTE_CENTRAL -> {
                securite.exiger(Role.COMPTABLE_ORDRE);
                modele = 25;
                ids = new HashSet<>(structures.findAll().stream().map(Structure::getId).toList());
                List<String> manquants = structures.findAll().stream().filter(Structure::isActive)
                        .filter(x -> redditions.findByStructureIdAndExerciceIdAndTypeReddition(x.getId(), e.getId(), TypeReddition.COMPTE_GESTION)
                                .map(r -> r.getStatut() != StatutOperation.VALIDE).orElse(true))
                        .map(Structure::getCode).toList();
                if (!manquants.isEmpty()) {
                    throw new BusinessException(ErrorCode.REGLE_METIER, "Comptes de gestion non validés : centralisation impossible", manquants);
                }
            }
            case COMPTE_ADMINISTRATIF -> {
                securite.exiger(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
                modele = 26;
                ids = securite.perimetre(s.getId());
            }
            default -> throw new IllegalStateException();
        }
        List<String> enCircuit = documents.findByStructureIdInAndExerciceIdAndStatutIn(ids, e.getId(), EN_CIRCUIT).stream()
                .filter(d -> d.getModele() < 23).map(d -> d.getNumero() + " (" + d.getStatut() + ")").toList();
        if (!enCircuit.isEmpty()) {
            throw new BusinessException(ErrorCode.REGLE_METIER, "Des pièces sont encore en circuit de validation", enCircuit);
        }
        Balance b = etats.balance(ids, e, s.getCode());
        if (!b.equilibree()) {
            throw new BusinessException(ErrorCode.REGLE_METIER, "Balance incohérente", b.anomalies());
        }
        Reddition r = redditions.findByStructureIdAndExerciceIdAndTypeReddition(s.getId(), e.getId(), type).orElseGet(Reddition::new);
        if (r.getId() != null && r.getStatut() != StatutOperation.ANNULE && r.getDocument() != null
                && r.getDocument().getStatut() != StatutWorkflow.REJETE) {
            throw new BusinessException(ErrorCode.CONFLIT, type + " déjà préparé pour " + s.getCode() + " " + e.getAnnee());
        }
        r.setStructure(s);
        r.setExercice(e);
        r.setTypeReddition(type);
        r.setStatut(StatutOperation.EN_COURS);
        DocumentReglementaire d = workflow.creer(modele, s, e, b.total().valeurFin(), false);
        r.setDocument(d);
        redditions.save(r);
        audit.tracer("PREPARATION", "Reddition", r.getId(), s.getCode(), type + " " + e.getAnnee() + " valeur=" + Montants.fcfa(b.total().valeurFin()));
        return dto(r);
    }

    @Override
    public boolean supporte(DocumentReglementaire d) {
        return d.getModele() >= 23 && d.getModele() <= 26;
    }

    @Override
    public void appliquer(DocumentReglementaire d) {
        redditions.findAll().stream().filter(r -> r.getDocument() != null && r.getDocument().getId().equals(d.getId())).findFirst()
                .ifPresent(r -> {
                    r.setStatut(StatutOperation.VALIDE);
                    r.setDateDepot(LocalDate.now());
                });
    }

    /** Clôture de l'exercice : fige les opérations et reporte l'existant en soldes d'ouverture N+1. */
    public void cloturer(Integer annee) {
        securite.exiger(Role.ADMIN, Role.COMPTABLE_ORDRE);
        Exercice e = referentiel.exerciceOuvert(annee);
        List<String> enCircuit = documents.findByExerciceIdAndStatutIn(e.getId(), EN_CIRCUIT).stream().map(DocumentReglementaire::getNumero).toList();
        if (!enCircuit.isEmpty()) {
            throw new BusinessException(ErrorCode.REGLE_METIER, "Clôture impossible : pièces en circuit", enCircuit);
        }
        Exercice suivant = exercices.findByAnnee(e.getAnnee() + 1).orElseGet(() -> {
            Exercice n = new Exercice();
            n.setAnnee(e.getAnnee() + 1);
            return exercices.save(n);
        });
        Map<Long, java.math.BigDecimal> netSuivant = new HashMap<>();
        for (EcritureJournal ej : journal.findByExerciceId(suivant.getId())) {
            if (ej.getBien() != null) netSuivant.merge(ej.getBien().getId(), ej.getQuantiteEntree().subtract(ej.getQuantiteSortie()), java.math.BigDecimal::add);
        }
        for (Bien b : biens.findAll()) {
            java.math.BigDecimal ouverture = b.getExistant().subtract(netSuivant.getOrDefault(b.getId(), java.math.BigDecimal.ZERO));
            if (ouverture.signum() == 0) continue;
            SoldeOuverture so = soldes.findByExerciceIdAndBienId(suivant.getId(), b.getId()).orElseGet(SoldeOuverture::new);
            so.setExercice(suivant);
            so.setBien(b);
            so.setQuantite(ouverture);
            so.setMontant(ouverture.multiply(b.getPrixUnitaire()));
            soldes.save(so);
        }
        e.setStatut(StatutExercice.CLOTURE);
        e.setDateCloture(LocalDate.now());
        audit.tracer("CLOTURE", "Exercice", e.getId(), null, "Exercice " + e.getAnnee() + " clôturé, report sur " + suivant.getAnnee());
    }
}
