package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.DashboardDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.util.Echeances;
import sn.masae.gcm.common.util.Montants;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tableau de bord dynamique filtrable (structure, région, département, exercice) avec alertes :
 * visas et scans en attente, sorties provisoires échues, stocks sous seuil, échéance du 30 juin.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Set<StatutWorkflow> EN_CIRCUIT = EnumSet.of(StatutWorkflow.BROUILLON, StatutWorkflow.SIGNE_COMMISSION,
            StatutWorkflow.EN_ATTENTE_VISA_EXTERNE, StatutWorkflow.SCAN_IMPORTE);

    private final StructureRepository structures;
    private final BienRepository biens;
    private final DocumentReglementaireRepository documents;
    private final MouvementRepository mouvements;
    private final EcritureJournalRepository journal;
    private final RedditionRepository redditions;
    private final CommissionRepository commissions;
    private final ReferentielService referentiel;
    private final SecuriteService securite;

    public DashboardService(StructureRepository structures, BienRepository biens, DocumentReglementaireRepository documents,
                            MouvementRepository mouvements, EcritureJournalRepository journal, RedditionRepository redditions,
                            CommissionRepository commissions, ReferentielService referentiel, SecuriteService securite) {
        this.structures = structures;
        this.biens = biens;
        this.documents = documents;
        this.mouvements = mouvements;
        this.journal = journal;
        this.redditions = redditions;
        this.commissions = commissions;
        this.referentiel = referentiel;
        this.securite = securite;
    }

    public Dashboard calculer(Long structureId, String region, String departement, Integer annee) {
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        Set<Long> perimetre = securite.perimetre(structureId);
        List<Structure> ss = structures.findAllById(perimetre).stream()
                .filter(s -> region == null || region.isBlank() || region.equalsIgnoreCase(s.getRegion()))
                .filter(s -> departement == null || departement.isBlank() || departement.equalsIgnoreCase(s.getDepartement()))
                .sorted(Comparator.comparing(Structure::getCode)).toList();
        Set<Long> ids = ss.stream().map(Structure::getId).collect(Collectors.toSet());
        LocalDate today = ReferentielService.aujourdHui();

        List<Bien> bs = ids.isEmpty() ? List.of() : biens.findByStructureIdInOrderByNumeroInventaireAsc(ids);
        List<DocumentReglementaire> docs = ids.isEmpty() ? List.of() : documents.findByStructureIdInAndExerciceIdAndStatutIn(ids, e.getId(), EN_CIRCUIT);
        List<Mouvement> sp = ids.isEmpty() ? List.of() : mouvements.findSortiesProvisoiresNonRetournees(ids);
        List<Bien> sousSeuil = ids.isEmpty() ? List.of() : biens.findSousSeuil(ids);
        List<EcritureJournal> ecritures = ids.isEmpty() ? List.of() : journal.findByStructureIdInAndExerciceIdOrderByStructureIdAscNumeroOrdreAsc(ids, e.getId());

        BigDecimal valeur = bs.stream().map(Bien::getValeurExistant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal entrees = ecritures.stream().map(x -> Montants.nz(x.getMontantEntree())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sorties = ecritures.stream().map(x -> Montants.nz(x.getMontantSortie())).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> parCompte = new TreeMap<>();
        Map<String, String> libelles = new HashMap<>();
        for (Bien b : bs) {
            String c = b.getCompte().getComptePrincipal();
            parCompte.merge(c, b.getValeurExistant(), BigDecimal::add);
            libelles.putIfAbsent(c, b.getCompte().getLibelle());
        }
        List<RepartitionCompte> repartition = parCompte.entrySet().stream()
                .map(en -> new RepartitionCompte(en.getKey(), libelles.get(en.getKey()), en.getValue())).toList();

        List<IndicateurStructure> parStructure = ss.stream().map(s -> new IndicateurStructure(s.getId(), s.getCode(), s.getLibelle(), s.getRegion(),
                bs.stream().filter(b -> b.getStructure().getId().equals(s.getId())).count(),
                bs.stream().filter(b -> b.getStructure().getId().equals(s.getId())).map(Bien::getValeurExistant).reduce(BigDecimal.ZERO, BigDecimal::add),
                docs.stream().filter(d -> d.getStructure().getId().equals(s.getId())).count(),
                sp.stream().filter(m -> m.getStructure().getId().equals(s.getId())).count(),
                redditions.findByStructureIdAndExerciceIdAndTypeReddition(s.getId(), e.getId(), TypeReddition.COMPTE_GESTION)
                        .map(r -> r.getStatut().name()).orElse("NON_PREPARE"))).toList();

        List<Alerte> alertes = new ArrayList<>();
        Echeances.StatutEcheance statutEch = Echeances.statut(e.getAnnee(), today);
        long jours = Echeances.joursRestants(e.getAnnee(), today);
        if (statutEch != Echeances.StatutEcheance.DANS_LES_DELAIS) {
            alertes.add(new Alerte("ECHEANCE", statutEch == Echeances.StatutEcheance.EN_RETARD ? "CRITIQUE" : "ATTENTION",
                    statutEch == Echeances.StatutEcheance.EN_RETARD
                            ? "Échéance du 30 juin dépassée : amende encourue jusqu'à " + Montants.fcfa(BigDecimal.valueOf(Echeances.amendeMaximale(e.getAnnee(), today))) + " FCFA par comptable"
                            : "Reddition des comptes " + e.getAnnee() + " à déposer avant le 30 juin (" + jours + " jours)", null, null, "/etats/reddition"));
        }
        for (DocumentReglementaire d : docs) {
            if (d.getStatut() == StatutWorkflow.EN_ATTENTE_VISA_EXTERNE) {
                alertes.add(new Alerte("VISA", "ATTENTION", d.getNumero() + " — " + d.getTitre() + " : visa externe / scan attendu",
                        d.getStructure().getCode(), d.getId(), "/documents/" + d.getId()));
            } else if (d.getStatut() == StatutWorkflow.SCAN_IMPORTE) {
                alertes.add(new Alerte("VALIDATION", "INFO", d.getNumero() + " — en attente de validation finale", d.getStructure().getCode(), d.getId(),
                        "/documents/" + d.getId()));
            }
        }
        for (Mouvement m : sp) {
            if (m.getDateRetourPrevue() != null && m.getDateRetourPrevue().isBefore(today)) {
                alertes.add(new Alerte("SORTIE_PROVISOIRE", "CRITIQUE", "Sortie provisoire " + m.getNumero() + " non retournée (retour prévu le "
                        + m.getDateRetourPrevue() + ")", m.getStructure().getCode(), m.getId(), "/mouvements/" + m.getId()));
            }
        }
        for (Bien b : sousSeuil) {
            alertes.add(new Alerte("STOCK", "ATTENTION", b.getDesignation() + " (" + b.getNumeroInventaire() + ") sous le seuil d'alerte : "
                    + Montants.quantite(b.getQteAttente().add(b.getQteService())) + " / " + Montants.quantite(b.getSeuilAlerte()),
                    b.getStructure().getCode(), b.getId(), "/biens/" + b.getId()));
        }
        for (Structure s : ss) {
            if (s.isActive() && commissions.findByStructureIdAndExerciceId(s.getId(), e.getId()).filter(CommissionService::complete).isEmpty()) {
                alertes.add(new Alerte("COMMISSION", "ATTENTION", "Commission de réception " + e.getAnnee() + " non constituée ou incomplète",
                        s.getCode(), s.getId(), "/referentiels/commissions"));
            }
        }
        return new Dashboard(e.getAnnee(), Echeances.echeanceReddition(e.getAnnee()), jours, statutEch.name(),
                Echeances.amendeMaximale(e.getAnnee(), today), ss.size(), bs.size(), valeur, entrees, sorties, docs.size(),
                docs.stream().filter(d -> d.getStatut() == StatutWorkflow.EN_ATTENTE_VISA_EXTERNE).count(),
                docs.stream().filter(d -> d.getStatut() == StatutWorkflow.EN_ATTENTE_VISA_EXTERNE).count(),
                docs.stream().filter(d -> d.getStatut() == StatutWorkflow.SCAN_IMPORTE).count(),
                sp.size(), sousSeuil.size(), repartition, parStructure, alertes);
    }
}
