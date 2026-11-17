package sn.masae.gcm.api.seed;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.config.GcmProperties;
import sn.masae.gcm.api.service.ReferentielService;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import automatique, au premier démarrage, des référentiels du classeur {@code modeles_comptabilite_matieres.xlsx} :
 * 26 structures et rattachements, acteurs pré-identifiés (ordonnateurs, comptables), nomenclature des comptes,
 * seuils réglementaires (art. 44-45) et comptes de démonstration par rôle. Idempotent : ne s'exécute que sur base vide.
 */
@Component
public class ReferentielSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ReferentielSeeder.class);
    public static final String CLASSEUR = "seed/modeles_comptabilite_matieres.xlsx";
    private static final Pattern COMPTABLE = Pattern.compile("^(Monsieur|Madame|Mademoiselle)\\s+([^,]+),\\s*(.*?),?\\s*matricule de solde\\s*([\\w .\\/]+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final StructureRepository structures;
    private final CompteNomenclatureRepository comptes;
    private final SeuilReglementaireRepository seuils;
    private final ExerciceRepository exercices;
    private final ActeurRepository acteurs;
    private final ActeurFonctionRepository fonctions;
    private final CommissionRepository commissions;
    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encoder;
    private final GcmProperties props;

    public ReferentielSeeder(StructureRepository structures, CompteNomenclatureRepository comptes, SeuilReglementaireRepository seuils,
                             ExerciceRepository exercices, ActeurRepository acteurs, ActeurFonctionRepository fonctions,
                             CommissionRepository commissions, UtilisateurRepository utilisateurs, PasswordEncoder encoder, GcmProperties props) {
        this.structures = structures;
        this.comptes = comptes;
        this.seuils = seuils;
        this.exercices = exercices;
        this.acteurs = acteurs;
        this.fonctions = fonctions;
        this.commissions = commissions;
        this.utilisateurs = utilisateurs;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (props.seed() && structures.count() == 0) {
            try (InputStream in = new ClassPathResource(CLASSEUR).getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
                importer(wb);
            }
        }
        creerAdministrateurInitial();
    }

    /** Crée le compte {@code admin} avec GCM_ADMIN_PASSWORD s'il n'existe pas encore (installation de production). */
    void creerAdministrateurInitial() {
        if (props.adminPassword() == null || props.adminPassword().isBlank() || utilisateurs.existsByLogin("admin")) {
            return;
        }
        structures.findAll().stream().filter(s -> "STR-001".equals(s.getCode())).findFirst().ifPresent(dage -> {
            Utilisateur u = new Utilisateur();
            u.setLogin("admin");
            u.setNomComplet("Administrateur fonctionnel");
            u.setStructure(dage);
            u.getRoles().add(Role.ADMIN);
            u.setMotDePasse(encoder.encode(props.adminPassword()));
            utilisateurs.save(u);
            LOG.info("Compte administrateur initial créé : changer son mot de passe dès la première connexion");
        });
    }

    /** Point d'entrée réutilisable (tests). */
    @Transactional
    public void importer(Workbook wb) {
        Exercice exercice = exercices.findByAnnee(ReferentielService.aujourdHui().getYear()).orElseGet(() -> {
            Exercice e = new Exercice();
            e.setAnnee(ReferentielService.aujourdHui().getYear());
            return exercices.save(e);
        });
        int nComptes = importerNomenclature(wb.getSheet("Référentiel_Nomenclature"));
        Map<String, Structure> ss = importerStructures(wb.getSheet("Référentiel_Structures"));
        importerSeuils();
        Map<FonctionActeur, Acteur> titulaires = importerActeurs(wb.getSheet("Référentiel_Acteurs"), ss);
        if (props.demoPassword() != null && !props.demoPassword().isBlank()) {
            creerDemo(ss, exercice, titulaires);
        }
        LOG.info("Seed GCM : {} structures, {} comptes, {} acteurs importés depuis {}", ss.size(), nComptes, acteurs.count(), CLASSEUR);
    }

    private int importerNomenclature(Sheet sh) {
        int n = 0;
        for (Row r : sh) {
            if (r.getRowNum() < 4) continue;
            // La clé (colonne H) est une formule : on la reconstitue depuis les cinq segments.
            String cle = String.join("-", texte(r, 0) == null ? "" : texte(r, 0), nz(texte(r, 1)), nz(texte(r, 2)), nz(texte(r, 3)), nz(texte(r, 4)));
            if (!cle.matches("\\d+(-\\d+){4}")) continue;
            if (comptes.findByNumeroComplet(cle).isPresent()) continue;
            CompteNomenclature c = new CompteNomenclature();
            c.setNumeroComplet(cle);
            c.setGroupe(Integer.valueOf(texte(r, 0)));
            c.setComptePrincipal(texte(r, 1));
            c.setCompteSecondaire(texte(r, 2));
            c.setCompteDivisionnaire(texte(r, 3));
            c.setSousCompte(texte(r, 4));
            c.setLibelle(texte(r, 5));
            c.setNatureUnite("Unité");
            comptes.save(c);
            n++;
        }
        return n;
    }

    private Map<String, Structure> importerStructures(Sheet sh) {
        Map<String, Structure> ss = new LinkedHashMap<>();
        Map<String, String> parents = new HashMap<>();
        Map<String, String> comptables = new HashMap<>();
        for (Row r : sh) {
            String code = texte(r, 0);
            if (code == null || !code.matches("STR-\\d{3}")) continue;
            Structure s = new Structure();
            s.setCode(code);
            String libelle = texte(r, 1).replaceAll("\\s+", " ").trim();
            s.setLibelle(libelle);
            Matcher m = Pattern.compile("\\(([^)]+)\\)\\s*$").matcher(libelle);
            if (m.find()) s.setSigle(m.group(1));
            s.setTypeStructure("Principale".equalsIgnoreCase(texte(r, 2)) ? TypeStructure.PRINCIPALE : TypeStructure.SECONDAIRE);
            String n = normaliser(libelle);
            boolean deconcentre = n.contains("departemental") || n.contains("regional") || n.contains("centre de formation");
            s.setTypeService(deconcentre ? TypeService.DECONCENTRE : TypeService.CENTRAL);
            s.setRegion("Dakar");
            s.setDepartement(departement(n));
            ss.put(code, structures.save(s));
            String parent = texte(r, 3);
            if (parent != null && parent.matches("STR-\\d{3}")) parents.put(code, parent);
            String comptable = texte(r, 4);
            if (comptable != null && !comptable.toLowerCase().contains("renseigner")) comptables.put(code, comptable);
        }
        parents.forEach((c, p) -> ss.get(c).setParent(ss.get(p)));
        comptables.forEach((code, txt) -> {
            Matcher m = COMPTABLE.matcher(txt.replaceAll("\\s+", " ").trim());
            if (!m.find()) {
                LOG.warn("Comptable non interprété pour {} : {}", code, txt);
                return;
            }
            Acteur a = acteur(m.group(2).trim(), m.group(1), m.group(4).trim());
            a.setTitre(m.group(3).trim());
            Structure s = ss.get(code);
            nommer(a, s, s.getTypeStructure() == TypeStructure.PRINCIPALE ? FonctionActeur.COMPTABLE_PRINCIPAL : FonctionActeur.COMPTABLE_SECONDAIRE,
                    "Classeur de référence");
        });
        return ss;
    }

    private void importerSeuils() {
        seuil(ReferentielService.SEUIL_CENTRAL, "Réception par commission — services centraux", TypeService.CENTRAL, "600000");
        seuil(ReferentielService.SEUIL_DECONCENTRE, "Réception par commission — services déconcentrés", TypeService.DECONCENTRE, "300000");
    }

    private void seuil(String code, String libelle, TypeService t, String montant) {
        if (seuils.findByCode(code).isPresent()) return;
        SeuilReglementaire s = new SeuilReglementaire();
        s.setCode(code);
        s.setLibelle(libelle);
        s.setTypeService(t);
        s.setMontant(new BigDecimal(montant));
        s.setReferenceTexte("Instruction n°0001/MFB/DGB du 26 mai 2023, art. 44-45");
        seuils.save(s);
    }

    private Map<FonctionActeur, Acteur> importerActeurs(Sheet sh, Map<String, Structure> ss) {
        Map<FonctionActeur, Acteur> t = new EnumMap<>(FonctionActeur.class);
        Structure principale = ss.values().stream().filter(s -> s.getTypeStructure() == TypeStructure.PRINCIPALE).findFirst().orElseThrow();
        for (Row r : sh) {
            if (r.getRowNum() < 4) continue;
            String nom = texte(r, 0);
            String role = texte(r, 1);
            if (nom == null || role == null || nom.toLowerCase().contains("renseigner")) continue;
            FonctionActeur f = switch (normaliser(role)) {
                case "ordonnateur principal" -> FonctionActeur.ORDONNATEUR_PRINCIPAL;
                case "ordonnateur delegue" -> FonctionActeur.ORDONNATEUR_DELEGUE;
                case "comptable principal" -> FonctionActeur.COMPTABLE_PRINCIPAL;
                default -> null;
            };
            if (f == null) continue;
            Optional<ActeurFonction> existant = fonctions.findByStructureIdAndFonctionAndActifTrue(principale.getId(), f).stream().findFirst();
            Acteur a = existant.map(ActeurFonction::getActeur).orElseGet(() -> acteur(nom, null, null));
            if (texte(r, 3) != null) a.setTitre(texte(r, 3));
            if (existant.isEmpty()) nommer(a, principale, f, "Classeur de référence");
            t.put(f, a);
        }
        return t;
    }

    /** Comptes de démonstration (un par rôle) et commission de démonstration de la structure principale. */
    private void creerDemo(Map<String, Structure> ss, Exercice exercice, Map<FonctionActeur, Acteur> t) {
        Structure dage = ss.get("STR-001");
        Structure bfpa = ss.get("STR-005");
        Acteur magasinier = nommer(acteur("Fichiste DÉMO", null, null), dage, FonctionActeur.MAGASINIER, "Démonstration");
        Acteur controleur = nommer(acteur("Contrôleur DÉMO", null, null), dage, FonctionActeur.CONTROLEUR_BUDGETAIRE, "Démonstration");
        Acteur comptableOrdre = acteur("Comptable d'ordre DÉMO", null, null);
        Acteur comptableBfpa = fonctions.findByStructureIdAndActifTrue(bfpa.getId()).stream().map(ActeurFonction::getActeur).findFirst().orElse(null);
        utilisateur("admin", "Administrateur fonctionnel", dage, null, Role.ADMIN);
        utilisateur("ordonnateur", "Ordonnateur principal", dage, t.get(FonctionActeur.ORDONNATEUR_PRINCIPAL), Role.ORDONNATEUR_PRINCIPAL);
        utilisateur("ordonnateur.delegue", "Ordonnateur délégué (DAGE)", dage, t.get(FonctionActeur.ORDONNATEUR_DELEGUE), Role.ORDONNATEUR_DELEGUE);
        utilisateur("comptable", "Comptable principal (DAGE)", dage, t.get(FonctionActeur.COMPTABLE_PRINCIPAL), Role.COMPTABLE_PRINCIPAL);
        utilisateur("comptable.bfpa", "Comptable secondaire (BFPA)", bfpa, comptableBfpa, Role.COMPTABLE_SECONDAIRE);
        utilisateur("comptable.ordre", "Comptable d'ordre", dage, comptableOrdre, Role.COMPTABLE_ORDRE);
        utilisateur("magasinier", "Magasinier-fichiste (DAGE)", dage, magasinier, Role.MAGASINIER);
        utilisateur("controle", "Contrôle (CBM / Inspection)", dage, controleur, Role.CONTROLE);
        Commission c = new Commission();
        c.setStructure(dage);
        c.setExercice(exercice);
        c.setReferenceDecision("Décision de démonstration n°001/" + exercice.getAnnee());
        c.setDateDecision(ReferentielService.aujourdHui().withDayOfYear(1));
        RoleCommission[] roles = {RoleCommission.PRESIDENT, RoleCommission.SUPPLEANT, RoleCommission.MEMBRE, RoleCommission.MEMBRE};
        String[] noms = {"Président commission DÉMO", "Suppléant commission DÉMO", "Membre commission DÉMO A", "Membre commission DÉMO B"};
        for (int i = 0; i < 4; i++) {
            CommissionMembre m = new CommissionMembre();
            m.setCommission(c);
            m.setActeur(acteur(noms[i], null, null));
            m.setRoleCommission(roles[i]);
            m.setRang(i + 1);
            c.getMembres().add(m);
            if (i == 0) utilisateur("commission", "Président de commission (DAGE)", dage, m.getActeur(), Role.MEMBRE_COMMISSION);
        }
        commissions.save(c);
    }

    private void utilisateur(String login, String nom, Structure s, Acteur a, Role role) {
        if (utilisateurs.existsByLogin(login)) return;
        Utilisateur u = new Utilisateur();
        u.setLogin(login);
        u.setNomComplet(a != null && !a.getNomComplet().contains("DÉMO") ? a.getNomComplet() : nom);
        u.setStructure(s);
        u.setActeur(a);
        u.getRoles().add(role);
        u.setMotDePasse(encoder.encode(props.demoPassword()));
        utilisateurs.save(u);
    }

    private Acteur acteur(String nomComplet, String civilite, String matricule) {
        Acteur a = new Acteur();
        String[] mots = nomComplet.trim().split("\\s+");
        StringBuilder nom = new StringBuilder();
        StringBuilder prenom = new StringBuilder();
        for (String m : mots) {
            boolean majuscules = m.equals(m.toUpperCase()) && m.chars().anyMatch(Character::isLetter);
            (majuscules ? nom : prenom).append(prenom.length() > 0 && !majuscules || nom.length() > 0 && majuscules ? " " : "").append(m);
        }
        if (nom.length() == 0) {
            // Format « Prénom(s) Nom » sans capitales : le dernier mot est le nom.
            int i = nomComplet.trim().lastIndexOf(' ');
            nom = new StringBuilder(i < 0 ? nomComplet.trim() : nomComplet.trim().substring(i + 1));
            prenom = new StringBuilder(i < 0 ? "" : nomComplet.trim().substring(0, i));
        }
        a.setNom(nom.toString());
        a.setPrenom(prenom.length() == 0 ? null : prenom.toString());
        a.setMatricule(matricule);
        if (civilite != null) a.setTitre(civilite);
        return acteurs.save(a);
    }

    private Acteur nommer(Acteur a, Structure s, FonctionActeur f, String acte) {
        ActeurFonction af = new ActeurFonction();
        af.setActeur(a);
        af.setStructure(s);
        af.setFonction(f);
        af.setReferenceActe(acte);
        af.setDateNomination(ReferentielService.aujourdHui().withDayOfYear(1));
        fonctions.save(af);
        return a;
    }

    private static String departement(String n) {
        for (String d : List.of("pikine", "guediawaye", "keur massar", "rufisque")) {
            if (n.contains(d)) return Character.toUpperCase(d.charAt(0)) + d.substring(1);
        }
        return "Dakar";
    }

    static String normaliser(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static final DataFormatter FMT = new DataFormatter(Locale.FRANCE);

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String texte(Row r, int col) {
        Cell c = r.getCell(col);
        if (c == null) return null;
        String v = FMT.formatCellValue(c).trim();
        return v.isEmpty() ? null : v;
    }
}
