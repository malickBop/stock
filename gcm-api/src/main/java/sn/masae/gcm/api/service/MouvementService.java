package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.MouvementDtos.*;
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
import java.util.*;

/**
 * Saisie des mouvements de matières et ouverture du document réglementaire associé :
 * entrée → PV de réception (M1), sortie définitive → M3, sortie provisoire → M4, affectation/reprise → M7, mutation → M8.
 * Le retour d'une sortie provisoire est validé directement par le comptable.
 */
@Service
@Transactional
public class MouvementService implements ImpactHandler {

    private static final Map<TypeMouvement, Integer> MODELE = Map.of(
            TypeMouvement.ENTREE, 1, TypeMouvement.SORTIE_DEFINITIVE, 3, TypeMouvement.SORTIE_PROVISOIRE, 4,
            TypeMouvement.AFFECTATION, 7, TypeMouvement.REPRISE, 7, TypeMouvement.MUTATION, 8);

    private final MouvementRepository mouvements;
    private final BienRepository biens;
    private final DocumentReglementaireRepository documents;
    private final ReferentielService referentiel;
    private final ActeurService acteurs;
    private final CommissionService commissions;
    private final StockService stock;
    private final WorkflowService workflow;
    private final SecuriteService securite;
    private final AuditService audit;

    public MouvementService(MouvementRepository mouvements, BienRepository biens, DocumentReglementaireRepository documents,
                            ReferentielService referentiel, ActeurService acteurs, CommissionService commissions, StockService stock,
                            @org.springframework.context.annotation.Lazy WorkflowService workflow, SecuriteService securite, AuditService audit) {
        this.mouvements = mouvements;
        this.biens = biens;
        this.documents = documents;
        this.referentiel = referentiel;
        this.acteurs = acteurs;
        this.commissions = commissions;
        this.stock = stock;
        this.workflow = workflow;
        this.securite = securite;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MouvementDto> lister(Long structureId, Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        return mouvements.findByStructureIdInAndExerciceIdOrderByDateMouvementDescIdDesc(securite.perimetre(structureId), e.getId())
                .stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public MouvementDto lire(Long id) {
        return dto(mouvement(id));
    }

    public Mouvement mouvement(Long id) {
        Mouvement m = mouvements.findById(id).orElseThrow(() -> new NotFoundException("Mouvement", id));
        securite.verifierPerimetre(m.getStructure().getId());
        return m;
    }

    public MouvementDto dto(Mouvement m) {
        Integer modele = MODELE.get(m.getTypeMouvement());
        DocumentReglementaire d = modele == null ? null : documents.findFirstByMouvementIdAndModele(m.getId(), modele).orElse(null);
        return MouvementDto.of(m, d);
    }

    /** Création d'un mouvement par le comptable des matières (ou le magasinier pour préparation). */
    public MouvementDto creer(MouvementRequest r) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.MAGASINIER);
        Mouvement m = construire(r, true);
        mouvements.save(m);
        Integer modele = MODELE.get(m.getTypeMouvement());
        if (modele != null) {
            BigDecimal montant = m.getMontantTotal();
            boolean commission = false;
            if (modele == 1) {
                BigDecimal seuil = referentiel.seuilReception(m.getStructure());
                commission = montant.compareTo(seuil) > 0;
                if (commission) {
                    // Art. 44-45 : réception par commission obligatoire au-delà du seuil.
                    commissions.commissionComplete(m.getStructure(), m.getExercice());
                }
            }
            DocumentReglementaire d = workflow.creer(modele, m.getStructure(), m.getExercice(), montant, commission);
            d.setMouvement(m);
            m.setStatut(StatutMouvement.EN_COURS);
        }
        audit.tracer("CREATION", "Mouvement", m.getId(), m.getStructure().getCode(), m.getTypeMouvement() + " " + m.getNumero()
                + " montant=" + Montants.fcfa(m.getMontantTotal()));
        return dto(m);
    }

    /** Validation directe (retours de sortie provisoire). */
    public MouvementDto validerDirect(Long id) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Mouvement m = mouvement(id);
        if (MODELE.containsKey(m.getTypeMouvement())) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Ce mouvement doit être validé via le circuit de son document réglementaire");
        }
        stock.appliquer(m);
        audit.tracer("VALIDATION", "Mouvement", m.getId(), m.getStructure().getCode(), m.getNumero());
        return dto(m);
    }

    public void annuler(Long id) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE);
        Mouvement m = mouvement(id);
        if (m.getStatut() == StatutMouvement.VALIDE) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Un mouvement validé ne peut être annulé : passer une écriture inverse");
        }
        m.setStatut(StatutMouvement.ANNULE);
        audit.tracer("ANNULATION", "Mouvement", m.getId(), m.getStructure().getCode(), m.getNumero());
    }

    /**
     * Construit un mouvement à partir de la requête, avec tous les contrôles de cohérence.
     *
     * @param controleSaisie vrai pour une saisie utilisateur (périmètre, exercice ouvert)
     */
    public Mouvement construire(MouvementRequest r, boolean controleSaisie) {
        Structure s = referentiel.structure(r.structureId());
        if (controleSaisie) securite.verifierPerimetre(s.getId());
        Exercice e = referentiel.exerciceOuvert(r.exercice());
        if (r.dateMouvement().getYear() != e.getAnnee()) {
            throw new BusinessException(ErrorCode.VALIDATION, "La date du mouvement doit appartenir à l'exercice " + e.getAnnee());
        }
        verifierSousType(r.typeMouvement(), r.sousType());
        Mouvement m = new Mouvement();
        m.setStructure(s);
        m.setExercice(e);
        m.setTypeMouvement(r.typeMouvement());
        m.setSousType(r.sousType());
        m.setDateMouvement(r.dateMouvement());
        m.setReferencePiece(r.referencePiece());
        m.setTiers(r.tiers());
        m.setReceptionnaire(r.receptionnaire());
        m.setDateRetourPrevue(r.dateRetourPrevue());
        m.setObservations(r.observations());
        if (r.structureDestinationId() != null) m.setStructureDestination(referentiel.structure(r.structureDestinationId()));
        if (r.detenteurSourceId() != null) m.setDetenteurSource(acteurs.acteur(r.detenteurSourceId()));
        if (r.detenteurDestinationId() != null) m.setDetenteurDestination(acteurs.acteur(r.detenteurDestinationId()));
        switch (r.typeMouvement()) {
            case SORTIE_PROVISOIRE -> {
                if (r.dateRetourPrevue() == null || !r.dateRetourPrevue().isAfter(r.dateMouvement())) {
                    throw new BusinessException(ErrorCode.VALIDATION, "Une sortie provisoire exige une date de retour prévue postérieure à la sortie");
                }
                if (r.receptionnaire() == null || r.receptionnaire().isBlank()) {
                    throw new BusinessException(ErrorCode.VALIDATION, "Le réceptionnaire de la sortie provisoire est obligatoire");
                }
            }
            case SORTIE_DEFINITIVE -> {
                if (r.receptionnaire() == null && r.tiers() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION, "Le bénéficiaire / réceptionnaire de la sortie est obligatoire");
                }
            }
            case AFFECTATION -> {
                if (m.getDetenteurDestination() == null) throw new BusinessException(ErrorCode.VALIDATION, "Le détenteur bénéficiaire est obligatoire");
            }
            case MUTATION -> {
                if (m.getStructureDestination() == null && m.getDetenteurDestination() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION, "La mutation exige une structure ou un détenteur de destination");
                }
            }
            case RETOUR -> {
                if (r.mouvementOrigineId() == null) throw new BusinessException(ErrorCode.VALIDATION, "Le retour doit référencer la sortie provisoire d'origine");
                Mouvement o = mouvement(r.mouvementOrigineId());
                if (o.getTypeMouvement() != TypeMouvement.SORTIE_PROVISOIRE || o.getStatut() != StatutMouvement.VALIDE) {
                    throw new BusinessException("Le mouvement d'origine doit être une sortie provisoire validée");
                }
                m.setMouvementOrigine(o);
            }
            default -> { }
        }
        for (LigneRequest lr : r.lignes()) {
            m.addLigne(ligne(m, lr));
        }
        m.setNumero(numero(m));
        return m;
    }

    private LigneMouvement ligne(Mouvement m, LigneRequest lr) {
        LigneMouvement l = new LigneMouvement();
        if (lr.bienId() != null) {
            Bien b = biens.findById(lr.bienId()).orElseThrow(() -> new NotFoundException("Bien", lr.bienId()));
            if (!b.getStructure().getId().equals(m.getStructure().getId())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Le bien " + b.getNumeroInventaire() + " n'appartient pas à la structure " + m.getStructure().getCode());
            }
            controlerDisponibilite(m, b, lr.quantite());
            l.setBien(b);
            l.setCompte(b.getCompte());
            l.setDesignation(b.getDesignation());
            l.setNatureUnite(b.getNatureUnite());
            l.setPrixUnitaire(m.getTypeMouvement() == TypeMouvement.ENTREE && lr.prixUnitaire() != null ? lr.prixUnitaire() : b.getPrixUnitaire());
        } else {
            if (m.getTypeMouvement() != TypeMouvement.ENTREE) {
                throw new BusinessException(ErrorCode.VALIDATION, "Seule une entrée peut créer un nouveau bien : sélectionner un bien de l'existant");
            }
            if (lr.compteId() == null || lr.designation() == null || lr.designation().isBlank() || lr.prixUnitaire() == null) {
                throw new BusinessException(ErrorCode.VALIDATION, "Compte, désignation et prix unitaire sont obligatoires pour un nouveau bien");
            }
            CompteNomenclature c = referentiel.compte(lr.compteId());
            l.setCompte(c);
            l.setDesignation(lr.designation().trim());
            l.setNatureUnite(lr.natureUnite() == null || lr.natureUnite().isBlank() ? (c.getNatureUnite() == null ? "Unité" : c.getNatureUnite()) : lr.natureUnite());
            l.setPrixUnitaire(lr.prixUnitaire());
        }
        l.setQuantite(lr.quantite());
        l.setMontant(Montants.montant(lr.quantite(), l.getPrixUnitaire()));
        l.setEtat(lr.etat());
        l.setObservation(lr.observation());
        return l;
    }

    /** Contrôle anticipé (à la saisie) ; le contrôle définitif a lieu à la validation. */
    private void controlerDisponibilite(Mouvement m, Bien b, BigDecimal q) {
        BigDecimal dispo = switch (m.getTypeMouvement()) {
            case SORTIE_DEFINITIVE -> EnumSet.of(SousTypeMouvement.VENTE, SousTypeMouvement.DESTRUCTION, SousTypeMouvement.CESSION)
                    .contains(m.getSousType()) ? b.getQteReforme() : b.getQteAttente().add(b.getQteService());
            case SORTIE_PROVISOIRE -> b.getQteAttente().add(b.getQteService());
            case AFFECTATION -> b.getQteAttente();
            case REPRISE, MUTATION -> b.getQteService();
            case RETOUR -> b.getQteSortieProvisoire();
            default -> null;
        };
        if (dispo != null && dispo.compareTo(q) < 0) {
            String msg = switch (m.getTypeMouvement()) {
                case AFFECTATION -> "Affectation impossible : le bien doit avoir été préalablement entré et être en attente d'affectation";
                case MUTATION -> "Mutation impossible : seuls les biens en service peuvent être mutés";
                default -> "Quantité disponible insuffisante";
            };
            throw new BusinessException(ErrorCode.STOCK_INSUFFISANT, msg + " (" + b.getNumeroInventaire() + " : disponible "
                    + Montants.quantite(dispo) + ", demandé " + Montants.quantite(q) + ")");
        }
    }

    private static void verifierSousType(TypeMouvement t, SousTypeMouvement s) {
        Set<SousTypeMouvement> ok = switch (t) {
            case ENTREE -> EnumSet.of(SousTypeMouvement.ACHAT, SousTypeMouvement.DON, SousTypeMouvement.LEGS, SousTypeMouvement.PRODUCTION,
                    SousTypeMouvement.TRANSFERT_RECU, SousTypeMouvement.EXCEDENT_INVENTAIRE, SousTypeMouvement.REPORT_A_NOUVEAU);
            case SORTIE_DEFINITIVE -> EnumSet.of(SousTypeMouvement.CONSOMMATION, SousTypeMouvement.VENTE, SousTypeMouvement.DESTRUCTION,
                    SousTypeMouvement.CESSION, SousTypeMouvement.PERTE, SousTypeMouvement.VOL, SousTypeMouvement.TRANSFERT_EMIS,
                    SousTypeMouvement.MANQUANT_INVENTAIRE);
            case SORTIE_PROVISOIRE -> EnumSet.of(SousTypeMouvement.PRET, SousTypeMouvement.REPARATION, SousTypeMouvement.EXPOSITION);
            case RETOUR -> EnumSet.of(SousTypeMouvement.RETOUR_PRET);
            case AFFECTATION -> EnumSet.of(SousTypeMouvement.AFFECTATION_DETENTEUR);
            case REPRISE -> EnumSet.of(SousTypeMouvement.REPRISE_DETENTEUR);
            case MUTATION -> EnumSet.of(SousTypeMouvement.MUTATION_INTERNE, SousTypeMouvement.TRANSFERT_EMIS);
        };
        if (!ok.contains(s)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sous-type " + s + " incompatible avec le type " + t);
        }
    }

    private String numero(Mouvement m) {
        String prefixe = switch (m.getTypeMouvement()) {
            case ENTREE -> "E";
            case SORTIE_DEFINITIVE -> "SD";
            case SORTIE_PROVISOIRE -> "SP";
            case RETOUR -> "R";
            case AFFECTATION -> "AF";
            case REPRISE -> "RP";
            case MUTATION -> "MU";
        };
        long n = mouvements.countByStructureIdAndExerciceId(m.getStructure().getId(), m.getExercice().getId()) + 1;
        return prefixe + "-" + m.getExercice().getAnnee() + "-" + String.format("%05d", n);
    }

    // ---- Impact à la validation finale -----------------------------------------------------

    @Override
    public boolean supporte(DocumentReglementaire d) {
        return d.getMouvement() != null && MODELE.containsValue(d.getModele());
    }

    @Override
    public void appliquer(DocumentReglementaire d) {
        Mouvement m = d.getMouvement();
        stock.appliquer(m);
        if (d.getModele() == 1) {
            // Le bon d'entrée (M2) est généré après clôture du PV de réception.
            DocumentReglementaire bon = workflow.creer(2, m.getStructure(), m.getExercice(), m.getMontantTotal(), false);
            bon.setMouvement(m);
            bon.setDocumentParent(d);
        }
    }
}
