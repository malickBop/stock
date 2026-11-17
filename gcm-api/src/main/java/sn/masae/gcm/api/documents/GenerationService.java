package sn.masae.gcm.api.documents;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.EtatDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.service.*;
import sn.masae.gcm.api.workflow.Circuit;
import sn.masae.gcm.api.workflow.Circuits;
import sn.masae.gcm.api.workflow.WorkflowService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.util.Echeances;
import sn.masae.gcm.common.util.Montants;
import sn.masae.gcm.documents.*;
import sn.masae.gcm.documents.DocumentModele.Cadre;
import sn.masae.gcm.documents.DocumentModele.Champ;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pré-remplissage des 26 modèles réglementaires à partir de la base, puis rendu PDF ou DOCX.
 * Les pièces (modèles 1-8, 13, 20, 21, 23-26) partent d'un document du circuit ; les états (9-12, 14-19, 22)
 * sont calculés pour une structure et un exercice.
 */
@Service
@Transactional(readOnly = true)
public class GenerationService {

    public static final String MINISTERE = "Ministère de l'Agriculture, de la Souveraineté alimentaire et de l'Élevage";
    public static final Set<Integer> ETATS = Set.of(9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 22);
    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, DocumentRenderer> RENDUS = Map.of("pdf", new PdfRenderer(), "docx", new DocxRenderer());

    private final WorkflowService workflow;
    private final EtatService etats;
    private final RedditionService redditions;
    private final ReferentielService referentiel;
    private final ActeurService acteurs;
    private final CommissionService commissions;
    private final SecuriteService securite;
    private final BienRepository biens;
    private final ImmeubleRepository immeubles;
    private final ActeurRepository acteurRepo;
    private final StructureRepository structures;

    public GenerationService(WorkflowService workflow, EtatService etats, RedditionService redditions, ReferentielService referentiel,
                             ActeurService acteurs, CommissionService commissions, SecuriteService securite, BienRepository biens,
                             ImmeubleRepository immeubles, ActeurRepository acteurRepo, StructureRepository structures) {
        this.workflow = workflow;
        this.etats = etats;
        this.redditions = redditions;
        this.referentiel = referentiel;
        this.acteurs = acteurs;
        this.commissions = commissions;
        this.securite = securite;
        this.biens = biens;
        this.immeubles = immeubles;
        this.acteurRepo = acteurRepo;
        this.structures = structures;
    }

    public record Fichier(String nom, String contentType, byte[] contenu) {
    }

    /** Pièce justificative d'un document du circuit. */
    public Fichier document(Long documentId, String format) {
        DocumentReglementaire d = workflow.document(documentId);
        securite.verifierPerimetre(d.getStructure().getId());
        return rendre(construire(d), format, d.getNumero());
    }

    /** État calculé (modèles 9-12, 14-19, 22). {@code cibleId} = détenteur pour le modèle 14. */
    public Fichier etat(int modele, Long structureId, Integer annee, Long cibleId, String format) {
        if (!ETATS.contains(modele)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Le modèle " + modele + " est une pièce de circuit : générez-le depuis son document");
        }
        Structure s = referentiel.structure(structureId);
        securite.verifierPerimetre(s.getId());
        Exercice e = annee == null ? referentiel.exerciceCourant() : referentiel.exercice(annee);
        return rendre(construireEtat(modele, s, e, cibleId), format, "M" + modele + "-" + s.getCode() + "-" + e.getAnnee());
    }

    /** Modèle vierge (pour usage papier), avec cadres de signature et de visa. */
    public Fichier vierge(int modele, String format) {
        SpecModele sp = SpecModele.de(modele);
        DocumentModele d = new DocumentModele(modele, sp.titre(), sp.articles(), MINISTERE, null, null,
                sp.champs().stream().map(c -> new Champ(c, null)).toList(), colonnes(modele, sp), List.of(), null,
                mentions(sp), cadres(modele, null, null, null), null);
        return rendre(d, format, "Modele_" + String.format("%02d", modele) + "_vierge");
    }

    Fichier rendre(DocumentModele d, String format, String base) {
        DocumentRenderer r = RENDUS.get(format == null ? "pdf" : format.toLowerCase(Locale.ROOT));
        if (r == null) throw new BusinessException(ErrorCode.VALIDATION, "Format non supporté : " + format + " (pdf ou docx)");
        return new Fichier(base.replaceAll("[^\\w.-]", "_") + "." + r.extension(), r.contentType(), r.rendre(d));
    }

    // ------------------------------------------------------------------ pièces du circuit

    DocumentModele construire(DocumentReglementaire d) {
        int n = d.getModele();
        SpecModele sp = SpecModele.de(n);
        Structure s = d.getStructure();
        Exercice e = d.getExercice();
        Mouvement m = d.getMouvement() != null ? d.getMouvement() : d.getCertificat() != null ? d.getCertificat().getMouvement() : null;
        List<String> cols = colonnes(n, sp);
        List<List<String>> lignes = new ArrayList<>();
        List<String> totaux = null;
        List<String> mentions = new ArrayList<>(mentions(sp));
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("n°", d.getNumero());
        valeurs.put("année", String.valueOf(e.getAnnee()));
        valeurs.put("exercice", String.valueOf(e.getAnnee()));
        valeurs.put("section", "");
        valeurs.put("chapitre", "");
        valeurs.put("visa", d.getVisas().stream().map(v -> v.getTypeVisa() + " " + nz(v.getReferenceVisa()) + " du " + date(v.getDateVisa()))
                .collect(Collectors.joining(" ; ")));
        valeurs.put("inspecteur", d.getVisas().stream().filter(v -> v.getTypeVisa() == TypeVisa.INSPECTEUR_VERIFICATEUR)
                .map(v -> nz(v.getAutorite())).findFirst().orElse(""));
        valeurs.put("commission", commissions.commission(s, e).map(c -> c.getMembres().stream()
                .map(cm -> cm.getActeur().getNomComplet() + " (" + libelle(cm.getRoleCommission().name()) + ")").collect(Collectors.joining(", "))).orElse(""));
        acteurs.titulaire(s, FonctionActeur.COMPTABLE_PRINCIPAL, FonctionActeur.COMPTABLE_SECONDAIRE)
                .ifPresent(a -> valeurs.put("comptable", a.getNomComplet()));
        acteurs.titulaire(s, FonctionActeur.ORDONNATEUR_PRINCIPAL, FonctionActeur.ORDONNATEUR_DELEGUE, FonctionActeur.ORDONNATEUR_SECONDAIRE)
                .ifPresent(a -> valeurs.put("ordonnateur", a.getNomComplet()));
        acteurs.titulaire(s, FonctionActeur.MAGASINIER).ifPresent(a -> valeurs.put("magasinier", a.getNomComplet()));
        valeurs.put("échéance", date(Echeances.echeanceReddition(e.getAnnee())));
        valeurs.put("service", s.getLibelle());
        valeurs.put("région", s.getRegion());

        if (m != null) {
            valeurs.put("date", date(m.getDateMouvement()));
            valeurs.put("fournisseur", nz(m.getTiers()));
            valeurs.put("référence", nz(m.getReferencePiece()));
            if (m.getDetenteurSource() != null) valeurs.put("détenteur a", m.getDetenteurSource().getNomComplet());
            Acteur dest = m.getDetenteurDestination();
            if (dest != null) {
                valeurs.put("détenteur", dest.getNomComplet());
                valeurs.put("détenteur b", dest.getNomComplet());
            }
            if (!m.getLignes().isEmpty()) valeurs.put("compte", m.getLignes().get(0).getCompte().getNumeroComplet());
            if (n == 2 || n == 3 || n == 4) {
                // Décomposition du n° de compte : Groupe / principal / secondaire / divisionnaire / sous-compte
                CompteNomenclature c = m.getLignes().isEmpty() ? null : m.getLignes().get(0).getCompte();
                if (c != null) {
                    valeurs.put("compte", c.getGroupe() + " / " + c.getComptePrincipal() + " / " + c.getCompteSecondaire() + " / "
                            + c.getCompteDivisionnaire() + " / " + c.getSousCompte() + " — " + c.getLibelle());
                }
            }
            BigDecimal qte = BigDecimal.ZERO;
            BigDecimal total = BigDecimal.ZERO;
            for (LigneMouvement l : m.getLignes()) {
                qte = qte.add(l.getQuantite());
                total = total.add(Montants.nz(l.getMontant()));
                String inv = l.getBien() == null ? "" : nz(l.getBien().getNumeroInventaire());
                lignes.add(switch (n) {
                    case 1 -> List.of(l.getDesignation(), q(l.getQuantite()), nz(l.getNatureUnite()), f(l.getPrixUnitaire()), f(l.getMontant()), nz(l.getObservation()));
                    case 7, 8 -> List.of(l.getCompte().getNumeroComplet(), inv, l.getDesignation(), nz(l.getNatureUnite()), q(l.getQuantite()), f(l.getPrixUnitaire()));
                    case 13 -> List.of(inv, l.getCompte().getNumeroComplet(), nz(m.getReferencePiece()), l.getDesignation(), nz(l.getNatureUnite()),
                            date(m.getDateMouvement()), q(l.getQuantite()), "");
                    case 21 -> List.of(l.getCompte().getNumeroComplet(), l.getDesignation(), q(l.getQuantite()), f(l.getMontant()));
                    default -> List.of(l.getDesignation(), inv.isEmpty() ? nz(l.getObservation()) : inv, q(l.getQuantite()), nz(l.getNatureUnite()),
                            f(l.getPrixUnitaire()), f(l.getMontant()));
                });
            }
            totaux = switch (n) {
                case 1 -> List.of("TOTAUX", q(qte), "", "", f(total), "");
                case 7, 8 -> List.of("TOTAUX", "", "", "", q(qte), "");
                case 13 -> null;
                case 21 -> List.of("TOTAUX", "", q(qte), f(total));
                default -> List.of("TOTAUX", "", q(qte), "", "", f(total));
            };
            if (n == 1) {
                valeurs.put("arrêté", "Arrêté le présent PV à " + q(qte) + " unité(s) représentant une valeur de " + f(total)
                        + " FCFA que nous certifions avoir réceptionnées.");
                mentions.add(0, valeurs.get("arrêté"));
            }
            if (n >= 2 && n <= 4) {
                String objet = (n == 2 ? "déclare ce jour, augmenter mes prises en charge de " : n == 3 ? "déclare ce jour, diminuer mes prises en charge de "
                        : "certifie la sortie provisoire de ");
                int i = mentions.indexOf(mentions.stream().filter(x -> x.startsWith("CERTIFICATIONS")).findFirst().orElse(""));
                mentions.add(i + 1, "Le comptable des matières, soussigné, " + objet + q(qte) + " unité(s) représentant une valeur de " + f(total) + " FCFA.");
            }
            if (n == 4 && m.getDateRetourPrevue() != null) mentions.add(0, "Date de retour prévue : " + date(m.getDateRetourPrevue()));
            if (n == 21 && d.getCertificat() != null) {
                mentions.add(0, "Objet : " + libelle(d.getCertificat().getObjet().name()) + " — Motif : " + nz(d.getCertificat().getMotif()));
            }
        }
        if (d.getReforme() != null) {
            Reforme r = d.getReforme();
            valeurs.put("date", date(r.getDateExecution() != null ? r.getDateExecution() : r.getDateProposition()));
            BigDecimal valeur = BigDecimal.ZERO;
            for (ReformeLigne l : r.getLignes()) {
                Bien b = l.getBien();
                BigDecimal v = Montants.montant(l.getQuantite(), l.getValeurUnitaire());
                valeur = valeur.add(v);
                if (n == 5) {
                    lignes.add(List.of(b.getCompte().getNumeroComplet(), b.getDesignation(), "", "", q(l.getQuantite()), f(l.getValeurUnitaire()), "", ""));
                } else {
                    boolean vente = r.getDecision() == DecisionReforme.VENTE;
                    boolean destruction = r.getDecision() == DecisionReforme.DESTRUCTION;
                    lignes.add(List.of(b.getDesignation() + " (" + nz(b.getNatureUnite()) + ")", q(l.getQuantite()), f(l.getValeurUnitaire()), f(v),
                            vente ? q(l.getQuantite()) : "", "", "", destruction ? q(l.getQuantite()) : ""));
                }
            }
            if (n == 5) mentions.add(0, "Motif de la proposition : " + nz(r.getMotif()));
            if (n == 6) {
                totaux = List.of("TOTAUX", "", "", f(valeur), "", "", f(r.getProduitVente()), "");
                mentions.add(0, "Décision : " + (r.getDecision() == null ? "" : libelle(r.getDecision().name()))
                        + (r.getReferenceQuittance() == null ? "" : " — Quittance n° " + r.getReferenceQuittance()));
            }
        }
        if (d.getRecensement() != null) {
            Recensement r = d.getRecensement();
            valeurs.put("date", date(r.getDateFin() != null ? r.getDateFin() : r.getDateDebut()));
            valeurs.put("comptable entrant", valeurs.getOrDefault("comptable", ""));
            BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            BigDecimal excedents = BigDecimal.ZERO;
            BigDecimal manquants = BigDecimal.ZERO;
            for (RecensementLigne l : r.getLignes()) {
                Bien b = l.getBien();
                BigDecimal phys = Montants.nz(l.getQtePhysique());
                BigDecimal theo = Montants.nz(l.getQteTheorique());
                BigDecimal diff = phys.subtract(theo);
                BigDecimal v = Montants.montant(diff, l.getPrixUnitaire());
                if (v.signum() > 0) excedents = excedents.add(v); else manquants = manquants.add(v.negate());
                BigDecimal[] x = {b.getQteAttente(), b.getQteService(), b.getQteSortieProvisoire(), phys, theo, diff};
                for (int i = 0; i < t.length; i++) t[i] = t[i].add(Montants.nz(x[i]));
                lignes.add(List.of(b.getCompte().getNumeroComplet(), b.getDesignation(), q(b.getQteAttente()), q(b.getQteService()),
                        q(b.getQteSortieProvisoire()), q(phys), q(theo), q(diff)));
            }
            totaux = List.of("TOTAUX", "", q(t[0]), q(t[1]), q(t[2]), q(t[3]), q(t[4]), q(t[5]));
            mentions.add(0, "Écarts valorisés : excédents " + f(excedents) + " FCFA, manquants " + f(manquants)
                    + " FCFA, solde " + f(excedents.subtract(manquants)) + " FCFA.");
            valeurs.put("inventaire physique", valeurs.get("commission"));
        }
        if (n >= 23 && n <= 26) {
            Rapport rp = reddition(n, s, e, valeurs);
            lignes.addAll(rp.lignes());
            totaux = rp.totaux();
            cols = rp.colonnes();
        }
        List<Champ> champs = sp.champs().stream().map(c -> new Champ(c, resoudre(c, valeurs))).toList();
        return new DocumentModele(n, sp.titre(), sp.articles(), MINISTERE, s.getCode() + " — " + s.getLibelle(), d.getNumero(), champs, cols,
                lignes, totaux, mentions, cadres(n, d, s, e), filigrane(d.getStatut()));
    }

    private record Rapport(List<String> colonnes, List<List<String>> lignes, List<String> totaux) {
    }

    private Rapport reddition(int n, Structure s, Exercice e, Map<String, String> v) {
        if (n == 23 || n == 25) {
            Collection<Long> perimetre = securite.perimetre(s.getId());
            Map<String, RedditionDto> rd = redditions.lister(e.getAnnee()).stream()
                    .filter(r -> r.type() == TypeReddition.COMPTE_GESTION).collect(Collectors.toMap(RedditionDto::structureCode, r -> r, (a, b) -> a));
            List<List<String>> l = new ArrayList<>();
            BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            for (Structure st : structures.findAllById(perimetre).stream().sorted(Comparator.comparing(Structure::getCode)).toList()) {
                RedditionDto r = rd.get(st.getCode());
                String statut = r == null ? "Non produit" : libelle(r.statut().name()) + (r.dateDepot() == null ? "" : " le " + date(r.dateDepot()));
                if (n == 23) {
                    l.add(List.of(st.getCode(), st.getLibelle(), "Compte de gestion " + e.getAnnee(), statut));
                } else {
                    LigneBalance b = etats.balance(List.of(st.getId()), e, st.getCode()).total();
                    t[0] = t[0].add(b.valeurDebut());
                    t[1] = t[1].add(b.valeurEntrees());
                    t[2] = t[2].add(b.valeurSorties());
                    t[3] = t[3].add(b.valeurFin());
                    l.add(List.of(st.getCode() + " — " + st.getLibelle(), f(b.valeurDebut()), f(b.valeurEntrees()), f(b.valeurSorties()), f(b.valeurFin()), statut));
                }
            }
            v.put("documents", n == 23 ? l.size() + " compte(s) de gestion attendu(s)" : "");
            return n == 23
                    ? new Rapport(List.of("Code", "Structure", "Pièce", "Situation"), l, null)
                    : new Rapport(SpecModele.de(25).colonnes(), l, List.of("TOTAL MINISTÈRE", f(t[0]), f(t[1]), f(t[2]), f(t[3]), ""));
        }
        Balance b = etats.balance(s.getId(), e.getAnnee());
        v.put("total existant", f(b.total().valeurFin()) + " FCFA");
        v.put("fonctions", n == 24 ? "Comptable des matières — " + s.getCode() : "Ordonnateur des matières — " + s.getCode());
        v.put("prénom et nom", v.getOrDefault("comptable", ""));
        v.put("ordonnateur des matières", v.getOrDefault("ordonnateur", ""));
        List<List<String>> l = b.lignes().stream().map(x -> List.of(x.compte(), x.libelle(), f(x.valeurDebut()), f(x.valeurEntrees()),
                f(x.valeurSorties()), f(x.valeurFin()))).toList();
        LigneBalance t = b.total();
        return new Rapport(List.of("N° compte", "Désignation", "Existant début", "Entrées", "Sorties définitives", "Existant fin"), l,
                List.of("TOTAUX", "", f(t.valeurDebut()), f(t.valeurEntrees()), f(t.valeurSorties()), f(t.valeurFin())));
    }

    // ------------------------------------------------------------------ états

    DocumentModele construireEtat(int n, Structure s, Exercice e, Long cibleId) {
        SpecModele sp = SpecModele.de(n);
        List<String> cols = colonnes(n, sp);
        List<List<String>> lignes = new ArrayList<>();
        List<String> totaux = null;
        List<Champ> champs = new ArrayList<>();
        Integer annee = e.getAnnee();
        switch (n) {
            case 9, 10, 15 -> {
                List<EcritureDto> j = etats.livreJournal(s.getId(), annee);
                if (n == 9) {
                    Map<String, List<EcritureDto>> parBien = j.stream().collect(Collectors.groupingBy(
                            x -> nz(x.numeroInventaire()) + "|" + x.compte(), TreeMap::new, Collectors.toList()));
                    parBien.forEach((k, xs) -> {
                        BigDecimal qe = somme(xs, EcritureDto::quantiteEntree), me = somme(xs, EcritureDto::montantEntree);
                        BigDecimal qs = somme(xs, EcritureDto::quantiteSortie), ms = somme(xs, EcritureDto::montantSortie);
                        lignes.add(List.of(xs.get(0).compte(), xs.get(0).libelle(), q(qe), f(me), q(qs), f(ms), q(qe.subtract(qs)), f(me.subtract(ms))));
                    });
                } else if (n == 10) {
                    for (EcritureDto x : j) {
                        boolean entree = x.quantiteEntree().signum() > 0;
                        BigDecimal qte = entree ? x.quantiteEntree() : x.quantiteSortie();
                        BigDecimal mt = entree ? x.montantEntree() : x.montantSortie();
                        String pu = qte.signum() == 0 ? "" : f(mt.divide(qte, 0, RoundingMode.HALF_UP));
                        lignes.add(List.of(date(x.date()), nz(x.referencePiece()), x.libelle(), entree ? q(qte) : "", entree ? pu : "", entree ? f(mt) : "",
                                entree ? "" : q(qte), entree ? "" : pu, entree ? "" : f(mt), ""));
                    }
                    champs.add(new Champ("N° compte à afficher", "Tous les comptes"));
                } else {
                    j.stream().filter(x -> x.compte().startsWith("2") && x.quantiteSortie().signum() > 0)
                            .forEach(x -> lignes.add(List.of(date(x.date()), x.libelle(), q(x.quantiteSortie()), f(x.montantSortie()), "")));
                    acteurs.titulaire(s, FonctionActeur.COMPTABLE_PRINCIPAL, FonctionActeur.COMPTABLE_SECONDAIRE)
                            .ifPresent(a -> champs.add(new Champ("Nom et fonction du comptable", a.getNomComplet() + ", comptable des matières")));
                }
            }
            case 11 -> {
                BigDecimal te = BigDecimal.ZERO, ts = BigDecimal.ZERO;
                for (EcritureDto x : etats.livreJournal(s.getId(), annee)) {
                    boolean entree = x.quantiteEntree().signum() > 0;
                    te = te.add(x.montantEntree());
                    ts = ts.add(x.montantSortie());
                    lignes.add(List.of(String.valueOf(x.numeroOrdre()), date(x.date()), x.compte(), x.libelle(), entree ? "Entrée" : "Sortie",
                            q(entree ? x.quantiteEntree() : x.quantiteSortie()), f(entree ? x.montantEntree() : x.montantSortie()), nz(x.referencePiece())));
                }
                totaux = List.of("TOTAUX", "", "", "", "", "", "E : " + f(te) + " / S : " + f(ts), "");
            }
            case 12 -> {
                Map<String, List<Bien>> parCompte = biens.findByStructureIdOrderByNumeroInventaireAsc(s.getId()).stream()
                        .collect(Collectors.groupingBy(b -> b.getCompte().getNumeroComplet(), TreeMap::new, Collectors.toList()));
                Map<String, List<EcritureDto>> j = etats.livreJournal(s.getId(), annee).stream().collect(Collectors.groupingBy(EcritureDto::compte));
                parCompte.forEach((c, bs) -> {
                    List<EcritureDto> xs = j.getOrDefault(c, List.of());
                    BigDecimal qte = bs.stream().map(Bien::getExistant).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal val = bs.stream().map(Bien::getValeurExistant).reduce(BigDecimal.ZERO, BigDecimal::add);
                    String retour = bs.stream().filter(b -> b.getQteSortieProvisoire().signum() > 0).map(Bien::getNumeroInventaire).collect(Collectors.joining(", "));
                    lignes.add(List.of(c, bs.get(0).getCompte().getLibelle(), q(somme(xs, EcritureDto::quantiteEntree)), q(somme(xs, EcritureDto::quantiteSortie)),
                            qte.signum() == 0 ? "" : f(val.divide(qte, 0, RoundingMode.HALF_UP)), q(qte), f(val), retour.isEmpty() ? "" : "SP : " + retour));
                });
            }
            case 14 -> {
                Acteur a = cibleId == null ? null : acteurRepo.findById(cibleId).orElse(null);
                List<Bien> bs = a == null ? List.of() : biens.findByDetenteurId(a.getId()).stream()
                        .filter(b -> securite.perimetre(s.getId()).contains(b.getStructure().getId())).toList();
                champs.add(new Champ("Nom et fonction du détenteur", a == null ? null : a.getNomComplet() + (a.getTitre() == null ? "" : ", " + a.getTitre())));
                for (Bien b : bs) {
                    lignes.add(List.of(b.getCompte().getNumeroComplet(), b.getDesignation(), q(b.getQteService()), "", f(b.getPrixUnitaire()),
                            q(b.getQteService()), date(b.getDateAcquisition()), nz(b.getNumeroInventaire())));
                }
            }
            case 16, 17, 18 -> {
                TypeImmeuble t = n == 16 ? TypeImmeuble.BATI : n == 17 ? TypeImmeuble.NON_BATI : TypeImmeuble.BAIL;
                for (Immeuble i : immeubles.findByStructureIdInAndTypeImmeubleOrderByReferenceAsc(List.of(s.getId()), t)) {
                    String compte = i.getCompte() == null ? "" : i.getCompte().getNumeroComplet();
                    lignes.add(switch (n) {
                        case 16 -> List.of(i.getReference(), compte, nz(i.getLocalisation()), i.getDesignation(), nz(i.getAffectation()),
                                i.getEtat() == null ? "" : libelle(i.getEtat().name()), "", f(i.getValeur()));
                        case 17 -> List.of(i.getReference(), compte, nz(i.getLocalisation()), q(i.getSuperficie()) + " m²", nz(i.getTitreFoncier()),
                                nz(i.getAffectation()), nz(i.getObservations()));
                        default -> List.of(i.getReference(), compte, nz(i.getLocalisation()), i.getDesignation(), nz(i.getAffectation()),
                                i.getEtat() == null ? "" : libelle(i.getEtat().name()), nz(i.getBailleur()) + (i.getLoyerMensuel() == null ? "" : " — loyer " + f(i.getLoyerMensuel())));
                    });
                }
            }
            case 19 -> {
                BigDecimal[] t = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                for (Bien b : biens.findByStructureIdOrderByNumeroInventaireAsc(s.getId())) {
                    if (b.getExistant().signum() == 0) continue;
                    t[0] = t[0].add(b.getQteAttente());
                    t[1] = t[1].add(b.getQteService());
                    t[2] = t[2].add(b.getQteSortieProvisoire());
                    t[3] = t[3].add(b.getExistant());
                    lignes.add(List.of(b.getCompte().getNumeroComplet(), b.getDesignation(), q(b.getQteAttente()), q(b.getQteService()),
                            q(b.getQteSortieProvisoire()), q(b.getExistant())));
                }
                totaux = List.of("TOTAUX", "", q(t[0]), q(t[1]), q(t[2]), q(t[3]));
                champs.add(new Champ("À la date du", date(ReferentielService.aujourdHui())));
            }
            case 22 -> {
                Balance b = etats.balance(s.getId(), annee);
                Map<String, List<Bien>> parCompte = biens.findByStructureIdOrderByNumeroInventaireAsc(s.getId()).stream()
                        .collect(Collectors.groupingBy(x -> x.getCompte().getNumeroComplet()));
                for (LigneBalance l : b.lignes()) {
                    List<Bien> bs = parCompte.getOrDefault(l.compte(), List.of());
                    lignes.add(List.of(l.compte(), l.libelle(), f(l.valeurDebut()), f(l.valeurEntrees()), f(l.valeurDebut().add(l.valeurEntrees())),
                            f(l.valeurSorties()), f(valeur(bs, Bien::getQteAttente)), f(valeur(bs, Bien::getQteService)),
                            f(valeur(bs, Bien::getQteSortieProvisoire)), f(l.valeurFin())));
                }
                LigneBalance t = b.total();
                totaux = List.of("TOTAUX", "", f(t.valeurDebut()), f(t.valeurEntrees()), f(t.valeurDebut().add(t.valeurEntrees())), f(t.valeurSorties()),
                        "", "", "", f(t.valeurFin()));
                if (!b.anomalies().isEmpty()) champs.add(new Champ("Anomalies", String.join(" ; ", b.anomalies())));
            }
            default -> throw new IllegalStateException();
        }
        champs.add(0, new Champ("Exercice", String.valueOf(annee)));
        return new DocumentModele(n, sp.titre(), sp.articles(), MINISTERE, s.getCode() + " — " + s.getLibelle(), null, champs, cols, lignes, totaux,
                mentions(sp), cadres(n, null, s, e), e.isCloture() ? null : "PROVISOIRE");
    }

    // ------------------------------------------------------------------ outils

    private List<Cadre> cadres(int n, DocumentReglementaire d, Structure s, Exercice e) {
        Circuit c = Circuits.pour(n).signaturesInternes().isEmpty() && Circuits.pour(n).visasExternes().isEmpty() ? null : Circuits.pour(n);
        List<Cadre> cadres = new ArrayList<>();
        List<RoleSignature> roles = c != null ? c.signaturesInternes() : List.of(RoleSignature.COMPTABLE, RoleSignature.ORDONNATEUR);
        for (RoleSignature r : roles) {
            Optional<DocumentSignature> sig = d == null ? Optional.empty()
                    : d.getSignatures().stream().filter(x -> x.getRoleSignature() == r).findFirst();
            String nom = sig.map(DocumentSignature::getSignataireNom).orElseGet(() -> s == null ? "" : titulaire(r, s));
            cadres.add(new Cadre(qualite(r), nom, sig.map(x -> "Signé le " + date(LocalDate.ofInstant(x.getDateSignature(), ZoneId.systemDefault()))).orElse("")));
        }
        if (c != null) {
            for (Set<TypeVisa> alternatives : c.visasExternes()) {
                Optional<DocumentVisaExterne> v = d == null ? Optional.empty()
                        : d.getVisas().stream().filter(x -> alternatives.contains(x.getTypeVisa())).findFirst();
                String q = "Visa " + alternatives.stream().map(t -> qualite(t)).sorted().collect(Collectors.joining(" ou "));
                cadres.add(new Cadre(q, v.map(x -> nz(x.getAutorite())).orElse(""),
                        v.map(x -> "Réf. " + nz(x.getReferenceVisa()) + " du " + date(x.getDateVisa())).orElse("")));
            }
        }
        return cadres;
    }

    private String titulaire(RoleSignature r, Structure s) {
        FonctionActeur[] fs = switch (r) {
            case COMPTABLE -> new FonctionActeur[]{FonctionActeur.COMPTABLE_PRINCIPAL, FonctionActeur.COMPTABLE_SECONDAIRE};
            case ORDONNATEUR -> new FonctionActeur[]{FonctionActeur.ORDONNATEUR_PRINCIPAL, FonctionActeur.ORDONNATEUR_DELEGUE, FonctionActeur.ORDONNATEUR_SECONDAIRE};
            case MAGASINIER -> new FonctionActeur[]{FonctionActeur.MAGASINIER};
            default -> new FonctionActeur[0];
        };
        return fs.length == 0 ? "" : acteurs.titulaire(s, fs).map(Acteur::getNomComplet).orElse("");
    }

    static String qualite(RoleSignature r) {
        return switch (r) {
            case PRESIDENT_COMMISSION -> "Le Président de la commission";
            case SUPPLEANT_COMMISSION -> "Le Suppléant";
            case MEMBRE_COMMISSION -> "Membre de la commission";
            case COMPTABLE -> "Le Comptable des matières";
            case ORDONNATEUR -> "L'Ordonnateur des matières";
            case MAGASINIER -> "Le Magasinier-fichiste";
            case DETENTEUR -> "Le Détenteur";
            case RECEPTIONNAIRE -> "Le Réceptionnaire";
        };
    }

    static String qualite(TypeVisa t) {
        return switch (t) {
            case CBM -> "du Contrôleur budgétaire";
            case CRF -> "du Contrôleur régional des finances";
            case DGB -> "de la Direction générale du Budget";
            case INSPECTEUR_VERIFICATEUR -> "de l'Inspecteur vérificateur";
            case RECEPTIONNAIRE -> "du Réceptionnaire";
        };
    }

    /** Associe un libellé du gabarit à une valeur connue (premier mot-clé contenu dans le libellé). */
    static String resoudre(String libelle, Map<String, String> valeurs) {
        String l = libelle.toLowerCase(Locale.FRENCH);
        List<String> cles = valeurs.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for (String k : cles) {
            if (l.contains(k)) return valeurs.get(k);
        }
        return null;
    }

    private static List<String> colonnes(int n, SpecModele sp) {
        return sp.colonnes();
    }

    private static List<String> mentions(SpecModele sp) {
        return sp.mentions().stream().filter(m -> m.length() >= 25).toList();
    }

    private static String filigrane(StatutWorkflow s) {
        return s == StatutWorkflow.VALIDATION_FINALE || s == StatutWorkflow.ARCHIVE ? null : "PROJET — " + libelle(s.name());
    }

    private static BigDecimal valeur(List<Bien> bs, Function<Bien, BigDecimal> qte) {
        return bs.stream().map(b -> Montants.montant(qte.apply(b), b.getPrixUnitaire())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal somme(List<EcritureDto> xs, Function<EcritureDto, BigDecimal> f) {
        return xs.stream().map(f).map(Montants::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String libelle(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(JJ_MM_AAAA);
    }

    private static String f(BigDecimal v) {
        return v == null ? "" : Montants.fcfa(v);
    }

    private static String q(BigDecimal v) {
        return v == null ? "" : Montants.quantite(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static Stream<Integer> tousLesModeles() {
        return SpecModele.tous().keySet().stream();
    }
}
