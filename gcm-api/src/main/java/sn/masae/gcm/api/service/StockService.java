package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.util.Montants;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.BienRepository;
import sn.masae.gcm.domain.repository.EcritureJournalRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Moteur de calcul de l'existant : applique l'impact comptable d'un mouvement validé sur les biens
 * et inscrit les écritures au livre-journal (numérotation ininterrompue par structure et exercice).
 * <p>Existant = entrées validées − sorties définitives validées ; les sorties provisoires, affectations,
 * reprises et mutations internes ne modifient pas l'existant mais sa ventilation.</p>
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StockService {

    private final BienRepository biens;
    private final EcritureJournalRepository journal;

    public StockService(BienRepository biens, EcritureJournalRepository journal) {
        this.biens = biens;
        this.journal = journal;
    }

    public void appliquer(Mouvement m) {
        if (m.getStatut() == StatutMouvement.VALIDE) {
            throw new BusinessException(ErrorCode.CONFLIT, "Le mouvement " + m.getNumero() + " est déjà validé");
        }
        if (m.getStatut() == StatutMouvement.ANNULE) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Le mouvement " + m.getNumero() + " est annulé");
        }
        if (m.getExercice().isCloture()) {
            throw new BusinessException(ErrorCode.EXERCICE_CLOTURE, "Exercice " + m.getExercice().getAnnee() + " clôturé");
        }
        for (LigneMouvement l : m.getLignes()) {
            switch (m.getTypeMouvement()) {
                case ENTREE -> entree(m, l);
                case SORTIE_DEFINITIVE -> sortieDefinitive(m, l);
                case SORTIE_PROVISOIRE -> sortieProvisoire(l);
                case RETOUR -> retour(l);
                case AFFECTATION -> affectation(m, l);
                case REPRISE -> reprise(l);
                case MUTATION -> mutation(m, l);
            }
        }
        m.setStatut(StatutMouvement.VALIDE);
        m.setDateValidation(Instant.now());
    }

    private void entree(Mouvement m, LigneMouvement l) {
        Bien b = l.getBien();
        if (b == null) {
            b = new Bien();
            b.setNumeroInventaire(numeroInventaire(m, l));
            b.setDesignation(l.getDesignation());
            b.setCompte(l.getCompte());
            b.setGroupe(l.getCompte().getGroupe());
            b.setStructure(m.getStructure());
            b.setNatureUnite(l.getNatureUnite());
            b.setPrixUnitaire(l.getPrixUnitaire());
            b.setEtat(l.getEtat() == null ? EtatBien.NEUF : l.getEtat());
            b.setDateAcquisition(m.getDateMouvement());
            biens.save(b);
            l.setBien(b);
        } else if (b.getExistant().signum() == 0) {
            b.setPrixUnitaire(l.getPrixUnitaire());
        } else if (l.getPrixUnitaire().compareTo(b.getPrixUnitaire()) != 0) {
            // Coût moyen pondéré pour les matières fongibles.
            BigDecimal valeur = b.getValeurExistant().add(l.getMontant());
            b.setPrixUnitaire(valeur.divide(b.getExistant().add(l.getQuantite()), 2, java.math.RoundingMode.HALF_UP));
        }
        b.setQteAttente(b.getQteAttente().add(l.getQuantite()));
        ecrire(m, l, b, true, m.getStructure());
    }

    private void sortieDefinitive(Mouvement m, LigneMouvement l) {
        Bien b = bienRequis(l);
        BigDecimal q = l.getQuantite();
        switch (m.getSousType()) {
            case VENTE, DESTRUCTION, CESSION -> retirer(b, q, Bien::getQteReforme, Bien::setQteReforme, "proposée/réformée");
            default -> retirerAttentePuisService(b, q);
        }
        ecrire(m, l, b, false, m.getStructure());
    }

    private void sortieProvisoire(LigneMouvement l) {
        Bien b = bienRequis(l);
        retirerAttentePuisService(b, l.getQuantite());
        b.setQteSortieProvisoire(b.getQteSortieProvisoire().add(l.getQuantite()));
    }

    private void retour(LigneMouvement l) {
        Bien b = bienRequis(l);
        retirer(b, l.getQuantite(), Bien::getQteSortieProvisoire, Bien::setQteSortieProvisoire, "en sortie provisoire");
        b.setQteAttente(b.getQteAttente().add(l.getQuantite()));
    }

    private void affectation(Mouvement m, LigneMouvement l) {
        Bien b = bienRequis(l);
        retirer(b, l.getQuantite(), Bien::getQteAttente, Bien::setQteAttente, "préalablement entrée et en attente d'affectation");
        b.setQteService(b.getQteService().add(l.getQuantite()));
        if (m.getDetenteurDestination() != null) {
            b.setDetenteur(m.getDetenteurDestination());
        }
    }

    private void reprise(LigneMouvement l) {
        Bien b = bienRequis(l);
        retirer(b, l.getQuantite(), Bien::getQteService, Bien::setQteService, "en service");
        b.setQteAttente(b.getQteAttente().add(l.getQuantite()));
        if (b.getQteService().signum() == 0) {
            b.setDetenteur(null);
        }
    }

    private void mutation(Mouvement m, LigneMouvement l) {
        Bien b = bienRequis(l);
        if (b.getQteService().compareTo(l.getQuantite()) < 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFISANT,
                    "Mutation impossible : seuls les biens en service peuvent être mutés (" + b.getNumeroInventaire() + ", en service : "
                            + Montants.quantite(b.getQteService()) + ")");
        }
        Structure dest = m.getStructureDestination();
        if (dest == null || dest.getId().equals(b.getStructure().getId())) {
            if (m.getDetenteurDestination() != null) b.setDetenteur(m.getDetenteurDestination());
            return;
        }
        // Mutation inter-structures : seule la sortie est passée ici ; l'entrée attend la confirmation de la structure destinataire.
        // Le détenteur est conservé : il retrouve le bien si la destinataire refuse la réception.
        b.setQteService(b.getQteService().subtract(l.getQuantite()));
        ecrire(m, l, b, false, b.getStructure());
    }

    /** Mutation refusée par la destinataire : les quantités réintégrées reprennent leur place en service. */
    public void remettreEnService(Mouvement reintegration) {
        for (LigneMouvement l : reintegration.getLignes()) {
            Bien b = bienRequis(l);
            retirer(b, l.getQuantite(), Bien::getQteAttente, Bien::setQteAttente, "en attente");
            b.setQteService(b.getQteService().add(l.getQuantite()));
        }
    }

    private Bien bienRequis(LigneMouvement l) {
        if (l.getBien() == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "La ligne « " + l.getDesignation() + " » doit référencer un bien de l'existant");
        }
        return l.getBien();
    }

    private void retirerAttentePuisService(Bien b, BigDecimal q) {
        BigDecimal dispo = b.getQteAttente().add(b.getQteService());
        if (dispo.compareTo(q) < 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFISANT, "Existant disponible insuffisant pour " + b.getNumeroInventaire() + " « "
                    + b.getDesignation() + " » : disponible " + Montants.quantite(dispo) + ", demandé " + Montants.quantite(q));
        }
        BigDecimal deAttente = b.getQteAttente().min(q);
        b.setQteAttente(b.getQteAttente().subtract(deAttente));
        b.setQteService(b.getQteService().subtract(q.subtract(deAttente)));
        if (b.getQteService().signum() == 0) b.setDetenteur(null);
    }

    private void retirer(Bien b, BigDecimal q, Function<Bien, BigDecimal> get, BiConsumer<Bien, BigDecimal> set, String situation) {
        if (get.apply(b).compareTo(q) < 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFISANT, "Quantité insuffisante pour " + b.getNumeroInventaire() + " « "
                    + b.getDesignation() + " » " + situation + " : disponible " + Montants.quantite(get.apply(b)) + ", demandé " + Montants.quantite(q));
        }
        set.accept(b, get.apply(b).subtract(q));
    }

    private void ecrire(Mouvement m, LigneMouvement l, Bien b, boolean entree, Structure structure) {
        EcritureJournal e = new EcritureJournal();
        e.setStructure(structure);
        e.setExercice(m.getExercice());
        e.setNumeroOrdre(journal.maxNumeroOrdre(structure.getId(), m.getExercice().getId()) + 1);
        e.setDateEcriture(m.getDateMouvement());
        e.setMouvement(m);
        e.setLigneMouvement(l);
        e.setCompte(l.getCompte());
        e.setBien(b);
        e.setLibelle(libelle(m) + " — " + l.getDesignation());
        e.setReferencePiece(m.getNumero() + (m.getReferencePiece() == null ? "" : " / " + m.getReferencePiece()));
        BigDecimal montant = Montants.montant(l.getQuantite(), entree ? l.getPrixUnitaire() : b.getPrixUnitaire());
        if (entree) {
            e.setQuantiteEntree(l.getQuantite());
            e.setMontantEntree(montant);
        } else {
            e.setQuantiteSortie(l.getQuantite());
            e.setMontantSortie(montant);
        }
        e.setCreatedBy(AuditService.loginCourant());
        journal.saveAndFlush(e);
    }

    private static String libelle(Mouvement m) {
        return switch (m.getTypeMouvement()) {
            case ENTREE -> "Entrée (" + m.getSousType() + ")";
            case SORTIE_DEFINITIVE -> "Sortie définitive (" + m.getSousType() + ")";
            case MUTATION -> "Mutation";
            default -> m.getTypeMouvement().name();
        };
    }

    private String numeroInventaire(Mouvement m, LigneMouvement l) {
        String base = m.getStructure().getCode() + "-" + m.getExercice().getAnnee() + "-" + l.getCompte().getComptePrincipal()
                + l.getCompte().getCompteSecondaire() + "-";
        long n = biens.count() + 1;
        String num;
        do {
            num = base + String.format("%05d", n++);
        } while (biens.findByNumeroInventaire(num).isPresent());
        return num;
    }
}
