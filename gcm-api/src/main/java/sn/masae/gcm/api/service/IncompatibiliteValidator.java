package sn.masae.gcm.api.service;

import org.springframework.stereotype.Component;
import sn.masae.gcm.common.exception.IncompatibiliteException;
import sn.masae.gcm.domain.entity.Acteur;
import sn.masae.gcm.domain.entity.ActeurFonction;
import sn.masae.gcm.domain.entity.Structure;
import sn.masae.gcm.domain.enums.FonctionActeur;
import sn.masae.gcm.domain.repository.ActeurFonctionRepository;
import sn.masae.gcm.domain.repository.LienParenteRepository;

import java.util.*;

import static sn.masae.gcm.domain.enums.FonctionActeur.*;

/**
 * Validateur bloquant des incompatibilités de fonctions (art. 18, 29, 32 et 36 de l'Instruction n°0001/MFB/DGB) :
 * <ul>
 *     <li>un même acteur ne peut cumuler les fonctions d'ordonnateur et de comptable des matières ;</li>
 *     <li>le comptable ne peut être magasinier-fichiste (séparation tenue des écritures / garde physique) ;</li>
 *     <li>les fonctions de contrôle (contrôleur budgétaire, inspecteur vérificateur) sont incompatibles avec la gestion ;</li>
 *     <li>l'ordonnateur et le comptable d'une structure ne peuvent siéger dans la commission de cette structure.</li>
 * </ul>
 * Un lien de parenté déclaré entre l'ordonnateur et le comptable d'une même chaîne hiérarchique génère une alerte.
 */
@Component
public class IncompatibiliteValidator {

    public enum Famille { ORDONNATEUR, COMPTABLE, MAGASINIER, CONTROLE, AUTRE }

    private static final Map<Famille, Set<Famille>> INCOMPATIBLES = Map.of(
            Famille.ORDONNATEUR, EnumSet.of(Famille.COMPTABLE, Famille.CONTROLE),
            Famille.COMPTABLE, EnumSet.of(Famille.ORDONNATEUR, Famille.MAGASINIER, Famille.CONTROLE),
            Famille.MAGASINIER, EnumSet.of(Famille.COMPTABLE, Famille.CONTROLE),
            Famille.CONTROLE, EnumSet.of(Famille.ORDONNATEUR, Famille.COMPTABLE, Famille.MAGASINIER),
            Famille.AUTRE, EnumSet.noneOf(Famille.class));

    private final ActeurFonctionRepository fonctions;
    private final LienParenteRepository liens;

    public IncompatibiliteValidator(ActeurFonctionRepository fonctions, LienParenteRepository liens) {
        this.fonctions = fonctions;
        this.liens = liens;
    }

    public static Famille famille(FonctionActeur f) {
        return switch (f) {
            case ORDONNATEUR_PRINCIPAL, ORDONNATEUR_DELEGUE, ORDONNATEUR_SECONDAIRE -> Famille.ORDONNATEUR;
            case COMPTABLE_ORDRE, COMPTABLE_PRINCIPAL, COMPTABLE_SECONDAIRE -> Famille.COMPTABLE;
            case MAGASINIER -> Famille.MAGASINIER;
            case CONTROLEUR_BUDGETAIRE, INSPECTEUR_VERIFICATEUR -> Famille.CONTROLE;
            default -> Famille.AUTRE;
        };
    }

    public static boolean incompatibles(FonctionActeur a, FonctionActeur b) {
        return INCOMPATIBLES.get(famille(a)).contains(famille(b));
    }

    /**
     * Vérifie qu'une nouvelle nomination est compatible avec les fonctions actives de l'acteur.
     *
     * @return les alertes non bloquantes (liens de parenté)
     * @throws IncompatibiliteException si un cumul interdit est détecté
     */
    public List<String> verifierNomination(Acteur acteur, FonctionActeur fonction, Structure structure) {
        List<String> violations = new ArrayList<>();
        for (ActeurFonction f : fonctions.findByActeurIdAndActifTrue(acteur.getId())) {
            if (incompatibles(fonction, f.getFonction())) {
                violations.add(acteur.getNomComplet() + " exerce déjà la fonction " + f.getFonction() + " (" + f.getStructure().getCode()
                        + "), incompatible avec " + fonction);
            }
        }
        if (!violations.isEmpty()) {
            throw new IncompatibiliteException("Cumul de fonctions incompatibles (art. 18, 29, 32, 36)", violations);
        }
        return alertesParente(acteur, fonction, structure);
    }

    private List<String> alertesParente(Acteur acteur, FonctionActeur fonction, Structure structure) {
        Famille fam = famille(fonction);
        if (fam != Famille.ORDONNATEUR && fam != Famille.COMPTABLE) {
            return List.of();
        }
        Famille opposee = fam == Famille.ORDONNATEUR ? Famille.COMPTABLE : Famille.ORDONNATEUR;
        List<String> alertes = new ArrayList<>();
        for (Structure s = structure; s != null; s = s.getParent()) {
            for (ActeurFonction f : fonctions.findByStructureIdAndActifTrue(s.getId())) {
                if (famille(f.getFonction()) == opposee && !f.getActeur().getId().equals(acteur.getId())
                        && liens.existeLien(acteur.getId(), f.getActeur().getId())) {
                    alertes.add("Lien de parenté déclaré entre " + acteur.getNomComplet() + " et " + f.getActeur().getNomComplet()
                            + " (" + f.getFonction() + ", " + s.getCode() + ") — situation à régulariser (art. 29)");
                }
            }
        }
        return alertes;
    }

    /** Un membre de commission ne peut être l'ordonnateur ou le comptable de la structure contrôlée. */
    public void verifierMembreCommission(Acteur acteur, Structure structure) {
        for (ActeurFonction f : fonctions.findByActeurIdAndActifTrue(acteur.getId())) {
            Famille fam = famille(f.getFonction());
            boolean memeStructure = f.getStructure().getId().equals(structure.getId());
            if ((fam == Famille.COMPTABLE || fam == Famille.MAGASINIER) && memeStructure || fam == Famille.ORDONNATEUR && memeStructure) {
                throw new IncompatibiliteException("Composition de commission irrégulière",
                        List.of(acteur.getNomComplet() + " est " + f.getFonction() + " de " + structure.getCode()
                                + " et ne peut siéger dans la commission de cette structure"));
            }
        }
    }

    /** Contrôle au moment d'une signature : le signataire ne doit pas cumuler les qualités d'ordonnateur et de comptable. */
    public void verifierSignataire(Long acteurId, Set<FonctionActeur> qualitesRequises) {
        List<ActeurFonction> actives = fonctions.findByActeurIdAndActifTrue(acteurId);
        for (ActeurFonction a : actives) {
            for (ActeurFonction b : actives) {
                if (incompatibles(a.getFonction(), b.getFonction())) {
                    throw new IncompatibiliteException("Signataire en situation d'incompatibilité",
                            List.of(a.getActeur().getNomComplet() + " cumule " + a.getFonction() + " et " + b.getFonction()));
                }
            }
        }
    }
}
