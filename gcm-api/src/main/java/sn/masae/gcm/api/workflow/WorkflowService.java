package sn.masae.gcm.api.workflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.masae.gcm.api.dto.DocumentDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.security.UtilisateurCourant;
import sn.masae.gcm.api.service.*;
import sn.masae.gcm.common.exception.AccessDeniedBusinessException;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static sn.masae.gcm.domain.enums.StatutWorkflow.*;

/**
 * Automate des circuits hybrides :
 * BROUILLON → SIGNE_COMMISSION → EN_ATTENTE_VISA_EXTERNE → SCAN_IMPORTE → VALIDATION_FINALE → ARCHIVE (REJETE possible avant validation).
 * Chaque transition est contrôlée (signatures, visas, scan, rôle, périmètre, exercice) et historisée.
 */
@Service
@Transactional
public class WorkflowService {

    private final DocumentReglementaireRepository documents;
    private final DocumentHistoriqueRepository historique;
    private final PieceJointeRepository pieces;
    private final PieceJointeService ged;
    private final CommissionService commissions;
    private final ActeurService acteurs;
    private final IncompatibiliteValidator incompatibilites;
    private final SecuriteService securite;
    private final AuditService audit;
    private final List<ImpactHandler> impacts;

    public WorkflowService(DocumentReglementaireRepository documents, DocumentHistoriqueRepository historique, PieceJointeRepository pieces,
                           PieceJointeService ged, CommissionService commissions, ActeurService acteurs,
                           IncompatibiliteValidator incompatibilites, SecuriteService securite, AuditService audit, List<ImpactHandler> impacts) {
        this.documents = documents;
        this.historique = historique;
        this.pieces = pieces;
        this.ged = ged;
        this.commissions = commissions;
        this.acteurs = acteurs;
        this.incompatibilites = incompatibilites;
        this.securite = securite;
        this.audit = audit;
        this.impacts = impacts;
    }

    // ---- Création --------------------------------------------------------------------------

    public DocumentReglementaire creer(int modele, Structure s, Exercice e, BigDecimal montant, boolean requiertCommission) {
        if (e.isCloture()) {
            throw new BusinessException(ErrorCode.EXERCICE_CLOTURE, "Exercice " + e.getAnnee() + " clôturé");
        }
        DocumentReglementaire d = new DocumentReglementaire();
        d.setModele(modele);
        d.setTitre(ModelesReglementaires.titre(modele));
        d.setStructure(s);
        d.setExercice(e);
        d.setMontantTotal(montant);
        d.setRequiertCommission(requiertCommission || Circuits.MODELES_COMMISSION.contains(modele));
        d.setNumero(numero(modele, s, e));
        if (!Circuits.pour(modele).avecCircuit()) {
            d.setStatut(VALIDATION_FINALE);
            d.setDateValidationFinale(Instant.now());
            d.setValidePar(AuditService.loginCourant());
        }
        documents.save(d);
        tracer(d, null, d.getStatut(), "CREATION", null);
        return d;
    }

    private String numero(int modele, Structure s, Exercice e) {
        long n = documents.countByModeleAndExerciceId(modele, e.getId()) + 1;
        String num;
        do {
            num = String.format("M%02d-%s-%d-%05d", modele, s.getCode(), e.getAnnee(), n++);
        } while (existeNumero(num));
        return num;
    }

    private boolean existeNumero(String num) {
        return documents.existsByNumero(num);
    }

    // ---- Lecture ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DocumentReglementaire document(Long id) {
        DocumentReglementaire d = documents.findById(id).orElseThrow(() -> new NotFoundException("Document", id));
        securite.verifierPerimetre(d.getStructure().getId());
        return d;
    }

    @Transactional(readOnly = true)
    public DocumentDto dto(Long id) {
        return dto(document(id));
    }

    public DocumentDto dto(DocumentReglementaire d) {
        UtilisateurCourant u = securite.courant();
        return new DocumentDto(d.getId(), d.getModele(), d.getNumero(), d.getTitre(), d.getStructure().getId(), d.getStructure().getCode(),
                d.getExercice().getAnnee(), d.getStatut(), d.isRequiertCommission(), d.getMontantTotal(), d.getNumeroVersion(),
                d.getMouvement() == null ? null : d.getMouvement().getId(), d.getRecensement() == null ? null : d.getRecensement().getId(),
                d.getReforme() == null ? null : d.getReforme().getId(), d.getCertificat() == null ? null : d.getCertificat().getId(),
                d.getCreatedAt(), d.getCreatedBy(), d.getDateValidationFinale(), d.getValidePar(), d.getMotifRejet(),
                d.getSignatures().stream().map(SignatureDto::of).toList(), d.getVisas().stream().map(VisaDto::of).toList(),
                pieces.findByDocumentIdOrderByCreatedAtAsc(d.getId()).stream().map(PieceDto::of).toList(),
                exigences(d), actionsPossibles(d, u));
    }

    @Transactional(readOnly = true)
    public List<HistoriqueDto> historique(Long id) {
        document(id);
        return historique.findByDocumentIdOrderByDateActionAsc(id).stream().map(HistoriqueDto::of).toList();
    }

    /** Liste des exigences du circuit et leur état, pour guider l'utilisateur. */
    public List<Exigence> exigences(DocumentReglementaire d) {
        Circuit c = Circuits.pour(d.getModele());
        List<Exigence> l = new ArrayList<>();
        for (RoleSignature r : signaturesRequises(d)) {
            long attendu = r == RoleSignature.MEMBRE_COMMISSION ? 2 : 1;
            long obtenu = d.getSignatures().stream().filter(s -> s.getRoleSignature() == r).count();
            l.add(new Exigence("Signature " + libelle(r) + (attendu > 1 ? " (" + obtenu + "/" + attendu + ")" : ""), obtenu >= attendu));
        }
        for (Set<TypeVisa> groupe : c.visasExternes()) {
            l.add(new Exigence("Visa externe " + String.join(" ou ", groupe.stream().map(Enum::name).sorted().toList()), visaPresent(d, groupe)));
        }
        if (c.scanRequis()) {
            l.add(new Exigence("Scan du document signé et visé", pieces.existsByDocumentIdAndTypePiece(d.getId(), TypePieceJointe.SCAN_SIGNE)));
        }
        if (c.avecCircuit()) {
            l.add(new Exigence("Validation finale (" + String.join(", ", c.validateurs().stream().map(Enum::name).sorted().toList()) + ")",
                    d.getDateValidationFinale() != null));
        }
        return l;
    }

    private List<RoleSignature> signaturesRequises(DocumentReglementaire d) {
        List<RoleSignature> l = new ArrayList<>();
        if (d.isRequiertCommission()) {
            l.addAll(List.of(RoleSignature.PRESIDENT_COMMISSION, RoleSignature.SUPPLEANT_COMMISSION, RoleSignature.MEMBRE_COMMISSION));
        }
        l.addAll(Circuits.pour(d.getModele()).signaturesInternes());
        return l;
    }

    private static boolean visaPresent(DocumentReglementaire d, Set<TypeVisa> groupe) {
        return d.getVisas().stream().anyMatch(v -> groupe.contains(v.getTypeVisa()));
    }

    private List<String> actionsPossibles(DocumentReglementaire d, UtilisateurCourant u) {
        List<String> a = new ArrayList<>();
        switch (d.getStatut()) {
            case BROUILLON -> a.add("SIGNER");
            case SIGNE_COMMISSION -> a.add("TRANSMETTRE");
            case EN_ATTENTE_VISA_EXTERNE -> { a.add("ENREGISTRER_VISA"); a.add("IMPORTER_SCAN"); }
            case SCAN_IMPORTE -> { if (u.a(Role.ADMIN) || u.roles().stream().anyMatch(Circuits.pour(d.getModele()).validateurs()::contains)) a.add("VALIDER"); }
            case VALIDATION_FINALE -> a.add("ARCHIVER");
            case REJETE -> a.add("REPRENDRE");
            default -> { }
        }
        if (EnumSet.of(BROUILLON, SIGNE_COMMISSION, EN_ATTENTE_VISA_EXTERNE, SCAN_IMPORTE).contains(d.getStatut())) a.add("REJETER");
        return a;
    }

    // ---- Transitions -----------------------------------------------------------------------

    /** Apposition d'une signature interne ; passage automatique à SIGNE_COMMISSION lorsque toutes sont réunies. */
    public DocumentDto signer(Long id, SignatureRequest r) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, BROUILLON);
        verifierExercice(d);
        UtilisateurCourant u = securite.courant();
        if (!signaturesRequises(d).contains(r.roleSignature())) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "La signature « " + libelle(r.roleSignature()) + " » n'est pas prévue pour ce document");
        }
        Acteur signataire = signataire(d, r, u);
        long attendu = r.roleSignature() == RoleSignature.MEMBRE_COMMISSION ? 2 : 1;
        if (d.getSignatures().stream().filter(s -> s.getRoleSignature() == r.roleSignature()).count() >= attendu) {
            throw new BusinessException(ErrorCode.CONFLIT, "Signature « " + libelle(r.roleSignature()) + " » déjà apposée");
        }
        if (d.getSignatures().stream().anyMatch(s -> s.getActeur() != null && s.getActeur().getId().equals(signataire.getId())
                && (Circuits.signatureCommission(s.getRoleSignature()) || Circuits.signatureCommission(r.roleSignature())))) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS, signataire.getNomComplet() + " a déjà signé ce document à un autre titre");
        }
        DocumentSignature s = new DocumentSignature();
        s.setDocument(d);
        s.setRoleSignature(r.roleSignature());
        s.setActeur(signataire);
        s.setSignataireNom(signataire.getNomComplet());
        s.setDateSignature(Instant.now());
        s.setUtilisateurLogin(u.login());
        d.getSignatures().add(s);
        tracer(d, d.getStatut(), d.getStatut(), "SIGNATURE", libelle(r.roleSignature()) + " : " + signataire.getNomComplet());
        if (signaturesCompletes(d)) {
            changer(d, SIGNE_COMMISSION, "SIGNATURES_COMPLETES", null);
        }
        return dto(d);
    }

    private Acteur signataire(DocumentReglementaire d, SignatureRequest r, UtilisateurCourant u) {
        RoleSignature role = r.roleSignature();
        if (Circuits.signatureCommission(role)) {
            Commission c = commissions.commissionComplete(d.getStructure(), d.getExercice());
            RoleCommission attendu = switch (role) {
                case PRESIDENT_COMMISSION -> RoleCommission.PRESIDENT;
                case SUPPLEANT_COMMISSION -> RoleCommission.SUPPLEANT;
                default -> RoleCommission.MEMBRE;
            };
            Long acteurId = r.acteurId() != null ? r.acteurId() : u.acteurId();
            return c.getMembres().stream().filter(m -> m.getRoleCommission() == attendu && m.getActeur().getId().equals(acteurId))
                    .map(CommissionMembre::getActeur).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMISSION_INCOMPLETE,
                            "Le signataire n'est pas " + attendu + " de la commission " + d.getStructure().getCode() + " " + d.getExercice().getAnnee()));
        }
        if (role == RoleSignature.COMPTABLE && !u.estComptable() && !u.a(Role.ADMIN)) {
            throw new AccessDeniedBusinessException("Seul le comptable des matières peut signer en cette qualité");
        }
        if (role == RoleSignature.ORDONNATEUR && !u.estOrdonnateur() && !u.a(Role.ADMIN)) {
            throw new AccessDeniedBusinessException("Seul l'ordonnateur peut signer en cette qualité");
        }
        Acteur a;
        if (role == RoleSignature.DETENTEUR && d.getMouvement() != null && d.getMouvement().getDetenteurDestination() != null) {
            a = d.getMouvement().getDetenteurDestination();
        } else if (u.acteurId() != null) {
            a = acteurs.acteur(u.acteurId());
        } else if (r.acteurId() != null) {
            a = acteurs.acteur(r.acteurId());
        } else {
            throw new BusinessException(ErrorCode.VALIDATION, "Aucun acteur associé à votre compte pour signer");
        }
        if (role == RoleSignature.COMPTABLE || role == RoleSignature.ORDONNATEUR) {
            incompatibilites.verifierSignataire(a.getId(), Set.of());
        }
        boolean aSigneCommeAutre = d.getSignatures().stream().anyMatch(s -> s.getActeur() != null && s.getActeur().getId().equals(a.getId())
                && (s.getRoleSignature() == RoleSignature.COMPTABLE && role == RoleSignature.ORDONNATEUR
                || s.getRoleSignature() == RoleSignature.ORDONNATEUR && role == RoleSignature.COMPTABLE));
        if (aSigneCommeAutre) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS, "Un même acteur ne peut signer comme ordonnateur et comme comptable");
        }
        return a;
    }

    private boolean signaturesCompletes(DocumentReglementaire d) {
        for (RoleSignature r : signaturesRequises(d)) {
            long attendu = r == RoleSignature.MEMBRE_COMMISSION ? 2 : 1;
            if (d.getSignatures().stream().filter(s -> s.getRoleSignature() == r).count() < attendu) return false;
        }
        return true;
    }

    /** Impression et transmission pour visa physique / signature du réceptionnaire. */
    public DocumentDto transmettre(Long id) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, SIGNE_COMMISSION);
        verifierExercice(d);
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.ORDONNATEUR_PRINCIPAL,
                Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
        changer(d, EN_ATTENTE_VISA_EXTERNE, "TRANSMISSION_VISA", "Document imprimé et transmis pour visa/signature physique");
        return dto(d);
    }

    /** Enregistrement d'un visa externe (CBM, CRF, DGB, vérificateur, réceptionnaire), avec son justificatif éventuel. */
    public DocumentDto enregistrerVisa(Long id, TypeVisa type, String autorite, String reference, LocalDate dateVisa, MultipartFile scan) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, EN_ATTENTE_VISA_EXTERNE);
        verifierExercice(d);
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.ORDONNATEUR_PRINCIPAL,
                Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
        Circuit c = Circuits.pour(d.getModele());
        if (c.visasExternes().stream().noneMatch(g -> g.contains(type))) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Le visa " + type + " n'est pas prévu pour le modèle " + d.getModele());
        }
        if (d.getVisas().stream().anyMatch(v -> v.getTypeVisa() == type)) {
            throw new BusinessException(ErrorCode.CONFLIT, "Visa " + type + " déjà enregistré");
        }
        if (dateVisa == null || dateVisa.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Date de visa invalide");
        }
        DocumentVisaExterne v = new DocumentVisaExterne();
        v.setDocument(d);
        v.setTypeVisa(type);
        v.setAutorite(autorite);
        v.setReferenceVisa(reference);
        v.setDateVisa(dateVisa);
        if (scan != null && !scan.isEmpty()) {
            v.setPieceJointe(ged.deposer(scan, TypePieceJointe.SCAN_SIGNE, d, null));
        }
        d.getVisas().add(v);
        tracer(d, d.getStatut(), d.getStatut(), "VISA_EXTERNE", type + (reference == null ? "" : " réf. " + reference));
        return dto(d);
    }

    /** Import du scan du document signé/visé ; bloqué si un visa externe requis manque. */
    public DocumentDto importerScan(Long id, MultipartFile scan) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, EN_ATTENTE_VISA_EXTERNE);
        verifierExercice(d);
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.ORDONNATEUR_PRINCIPAL,
                Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
        List<String> manquants = Circuits.pour(d.getModele()).visasExternes().stream().filter(g -> !visaPresent(d, g))
                .map(g -> "Visa " + String.join(" ou ", g.stream().map(Enum::name).sorted().toList())).toList();
        if (!manquants.isEmpty()) {
            throw new BusinessException(ErrorCode.PIECE_MANQUANTE, "Visa externe manquant : import du scan impossible", manquants);
        }
        PieceJointe p = ged.deposer(scan, TypePieceJointe.SCAN_SIGNE, d, null);
        changer(d, SCAN_IMPORTE, "IMPORT_SCAN", p.getNomFichier() + " (sha256 " + p.getSha256().substring(0, 12) + "…)");
        return dto(d);
    }

    /** Validation finale par le rôle habilité : déclenche l'impact comptable. */
    public DocumentDto valider(Long id, String commentaire) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, SCAN_IMPORTE);
        verifierExercice(d);
        Circuit c = Circuits.pour(d.getModele());
        UtilisateurCourant u = securite.exiger(c.validateurs().toArray(Role[]::new));
        List<String> manquantes = exigences(d).stream().filter(e -> !e.satisfaite() && !e.libelle().startsWith("Validation finale"))
                .map(Exigence::libelle).toList();
        if (!manquantes.isEmpty()) {
            throw new BusinessException(ErrorCode.PIECE_MANQUANTE, "Validation finale impossible : exigences non satisfaites", manquantes);
        }
        if (d.getSignatures().stream().anyMatch(s -> s.getRoleSignature() == RoleSignature.COMPTABLE && s.getActeur() != null
                && s.getActeur().getId().equals(u.acteurId())) && c.validateurs().stream().anyMatch(r -> r.name().startsWith("ORDONNATEUR"))
                && !u.a(Role.ADMIN)) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS, "Le comptable signataire ne peut valider en qualité d'ordonnateur");
        }
        d.setDateValidationFinale(Instant.now());
        d.setValidePar(u.login());
        changer(d, VALIDATION_FINALE, "VALIDATION_FINALE", commentaire);
        impacts.stream().filter(h -> h.supporte(d)).forEach(h -> h.appliquer(d));
        return dto(d);
    }

    public DocumentDto archiver(Long id) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, VALIDATION_FINALE);
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.ORDONNATEUR_PRINCIPAL,
                Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
        changer(d, ARCHIVE, "ARCHIVAGE", null);
        return dto(d);
    }

    public DocumentDto rejeter(Long id, String motif) {
        DocumentReglementaire d = document(id);
        if (!EnumSet.of(BROUILLON, SIGNE_COMMISSION, EN_ATTENTE_VISA_EXTERNE, SCAN_IMPORTE).contains(d.getStatut())) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE, "Un document validé ne peut plus être rejeté");
        }
        securite.exiger(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE, Role.COMPTABLE_PRINCIPAL,
                Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.CONTROLE);
        d.setMotifRejet(motif);
        changer(d, REJETE, "REJET", motif);
        return dto(d);
    }

    /** Reprise d'un document rejeté : nouvelle version, signatures et visas réinitialisés (historique conservé). */
    public DocumentDto reprendre(Long id) {
        DocumentReglementaire d = document(id);
        exigerStatut(d, REJETE);
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE);
        d.getSignatures().clear();
        d.getVisas().clear();
        d.setNumeroVersion(d.getNumeroVersion() + 1);
        d.setMotifRejet(null);
        changer(d, BROUILLON, "REPRISE", "Version " + d.getNumeroVersion());
        return dto(d);
    }

    // ---- Utilitaires -----------------------------------------------------------------------

    private void exigerStatut(DocumentReglementaire d, StatutWorkflow attendu) {
        if (d.getStatut() != attendu) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_INVALIDE,
                    "Action impossible : le document " + d.getNumero() + " est à l'état " + d.getStatut() + " (attendu : " + attendu + ")");
        }
    }

    private void verifierExercice(DocumentReglementaire d) {
        if (d.getExercice().isCloture()) {
            throw new BusinessException(ErrorCode.EXERCICE_CLOTURE, "Exercice " + d.getExercice().getAnnee() + " clôturé");
        }
    }

    private void changer(DocumentReglementaire d, StatutWorkflow nouveau, String action, String commentaire) {
        StatutWorkflow ancien = d.getStatut();
        d.setStatut(nouveau);
        documents.save(d);
        tracer(d, ancien, nouveau, action, commentaire);
    }

    private void tracer(DocumentReglementaire d, StatutWorkflow ancien, StatutWorkflow nouveau, String action, String commentaire) {
        DocumentHistorique h = new DocumentHistorique();
        h.setDocument(d);
        h.setAncienStatut(ancien);
        h.setNouveauStatut(nouveau);
        h.setAction(action);
        h.setCommentaire(commentaire);
        h.setUtilisateurLogin(AuditService.loginCourant());
        historique.save(h);
        audit.tracer(action, "Document", d.getId(), d.getStructure().getCode(), d.getNumero() + " " + ancien + " -> " + nouveau);
    }

    public static String libelle(RoleSignature r) {
        return switch (r) {
            case PRESIDENT_COMMISSION -> "Président de la commission";
            case SUPPLEANT_COMMISSION -> "Suppléant de la commission";
            case MEMBRE_COMMISSION -> "Membre de la commission";
            case COMPTABLE -> "Comptable des matières";
            case ORDONNATEUR -> "Ordonnateur";
            case MAGASINIER -> "Magasinier-fichiste";
            case DETENTEUR -> "Détenteur";
            case RECEPTIONNAIRE -> "Réceptionnaire";
        };
    }
}
