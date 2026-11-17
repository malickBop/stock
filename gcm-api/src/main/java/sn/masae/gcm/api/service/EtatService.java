package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.EtatDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Calcul des états de synthèse à partir du livre-journal et des soldes d'ouverture :
 * livre-journal (M11), grand-livre (M12), fiche de stock (M9), balance générale (M22) et
 * agrégats du compte de gestion (M24) / compte central (M25).
 */
@Service
@Transactional(readOnly = true)
public class EtatService {

    private final EcritureJournalRepository journal;
    private final SoldeOuvertureRepository soldes;
    private final BienRepository biens;
    private final StructureRepository structures;
    private final ReferentielService referentiel;
    private final SecuriteService securite;

    public EtatService(EcritureJournalRepository journal, SoldeOuvertureRepository soldes, BienRepository biens,
                       StructureRepository structures, ReferentielService referentiel, SecuriteService securite) {
        this.journal = journal;
        this.soldes = soldes;
        this.biens = biens;
        this.structures = structures;
        this.referentiel = referentiel;
        this.securite = securite;
    }

    public List<EcritureDto> livreJournal(Long structureId, Integer annee) {
        Exercice e = exercice(annee);
        return journal.findByStructureIdInAndExerciceIdOrderByStructureIdAscNumeroOrdreAsc(securite.perimetre(structureId), e.getId())
                .stream().map(EcritureDto::of).toList();
    }

    public List<EcritureDto> grandLivre(Long structureId, Integer annee, Long compteId) {
        securite.verifierPerimetre(structureId);
        Exercice e = exercice(annee);
        return journal.findByStructureIdAndExerciceIdAndCompteIdOrderByNumeroOrdreAsc(structureId, e.getId(), compteId)
                .stream().map(EcritureDto::of).toList();
    }

    public FicheStock ficheStock(Long bienId, Integer annee) {
        Bien b = biens.findById(bienId).orElseThrow(() -> new NotFoundException("Bien", bienId));
        securite.verifierPerimetre(b.getStructure().getId());
        Exercice e = exercice(annee);
        BigDecimal debut = soldes.findByExerciceIdAndBienId(e.getId(), bienId).map(SoldeOuverture::getQuantite).orElse(BigDecimal.ZERO);
        List<EcritureDto> mvts = journal.findByBienIdAndExerciceIdOrderByNumeroOrdreAsc(bienId, e.getId()).stream().map(EcritureDto::of).toList();
        BigDecimal fin = debut;
        for (EcritureDto m : mvts) fin = fin.add(m.quantiteEntree()).subtract(m.quantiteSortie());
        return new FicheStock(b.getId(), b.getNumeroInventaire(), b.getDesignation(), b.getCompte().getNumeroComplet(), b.getNatureUnite(),
                debut, mvts, fin, fin.multiply(b.getPrixUnitaire()));
    }

    /** Balance générale des comptes pour un périmètre (une structure et ses descendantes, ou tout le ministère). */
    public Balance balance(Long structureId, Integer annee) {
        Exercice e = exercice(annee);
        Set<Long> ids = securite.perimetre(structureId);
        return balance(ids, e, structureId == null ? "Ministère" : structures.findById(structureId).map(Structure::getCode).orElse("?"));
    }

    public Balance balance(Collection<Long> ids, Exercice e, String libellePerimetre) {
        Map<String, BigDecimal[]> acc = new TreeMap<>();
        Map<String, CompteNomenclature> comptes = new HashMap<>();
        for (Long sid : ids) {
            for (SoldeOuverture so : soldes.findByExerciceIdAndBienStructureId(e.getId(), sid)) {
                CompteNomenclature c = so.getBien().getCompte();
                comptes.put(c.getNumeroComplet(), c);
                BigDecimal[] v = acc.computeIfAbsent(c.getNumeroComplet(), k -> zeros());
                v[0] = v[0].add(so.getQuantite());
                v[1] = v[1].add(so.getMontant());
            }
        }
        for (EcritureJournal ej : journal.findByStructureIdInAndExerciceIdOrderByStructureIdAscNumeroOrdreAsc(ids, e.getId())) {
            CompteNomenclature c = ej.getCompte();
            comptes.put(c.getNumeroComplet(), c);
            BigDecimal[] v = acc.computeIfAbsent(c.getNumeroComplet(), k -> zeros());
            v[2] = v[2].add(nz(ej.getQuantiteEntree()));
            v[3] = v[3].add(nz(ej.getMontantEntree()));
            v[4] = v[4].add(nz(ej.getQuantiteSortie()));
            v[5] = v[5].add(nz(ej.getMontantSortie()));
        }
        List<LigneBalance> lignes = new ArrayList<>();
        BigDecimal[] t = zeros();
        List<String> anomalies = new ArrayList<>();
        for (var en : acc.entrySet()) {
            BigDecimal[] v = en.getValue();
            CompteNomenclature c = comptes.get(en.getKey());
            BigDecimal qFin = v[0].add(v[2]).subtract(v[4]);
            BigDecimal vFin = v[1].add(v[3]).subtract(v[5]);
            if (qFin.signum() < 0 || vFin.signum() < 0) anomalies.add("Solde négatif sur le compte " + en.getKey());
            lignes.add(new LigneBalance(en.getKey(), c.getLibelle(), c.getGroupe(), v[0], v[1], v[2], v[3], v[4], v[5], qFin, vFin));
            for (int i = 0; i < 6; i++) t[i] = t[i].add(v[i]);
        }
        BigDecimal valeurExistant = ids.stream().flatMap(id -> biens.findByStructureIdOrderByNumeroInventaireAsc(id).stream())
                .map(Bien::getValeurExistant).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vFinTotal = t[1].add(t[3]).subtract(t[5]);
        LigneBalance total = new LigneBalance("TOTAL", "Total général", null, t[0], t[1], t[2], t[3], t[4], t[5],
                t[0].add(t[2]).subtract(t[4]), vFinTotal);
        if (e.getStatut() == sn.masae.gcm.domain.enums.StatutExercice.OUVERT && vFinTotal.subtract(valeurExistant).abs().compareTo(BigDecimal.ONE) > 0) {
            anomalies.add("Écart entre la balance (" + vFinTotal + ") et l'existant valorisé (" + valeurExistant + ")");
        }
        return new Balance(e.getAnnee(), libellePerimetre, lignes, total, anomalies.isEmpty(), anomalies);
    }

    private Exercice exercice(Integer annee) {
        return annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] v = new BigDecimal[6];
        Arrays.fill(v, BigDecimal.ZERO);
        return v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
