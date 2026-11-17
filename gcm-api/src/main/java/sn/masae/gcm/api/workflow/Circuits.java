package sn.masae.gcm.api.workflow;

import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.enums.RoleSignature;
import sn.masae.gcm.domain.enums.TypeVisa;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static sn.masae.gcm.domain.enums.RoleSignature.*;

/**
 * Catalogue des circuits de validation par modèle (instructions.docx, § circuits hybrides).
 * Les modèles absents sont des états générés à la demande, sans circuit de signature.
 */
public final class Circuits {

    private static final Set<Role> ORDO = Set.of(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
    private static final Set<Role> COMPTA = Set.of(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE);
    private static final Set<TypeVisa> CBM_CRF = Set.of(TypeVisa.CBM, TypeVisa.CRF);

    private static final Map<Integer, Circuit> CIRCUITS = Map.ofEntries(
            Map.entry(1, new Circuit(List.of(COMPTABLE), List.of(CBM_CRF), true, ORDO)),
            Map.entry(3, new Circuit(List.of(COMPTABLE, ORDONNATEUR), List.of(Set.of(TypeVisa.RECEPTIONNAIRE)), true, COMPTA)),
            Map.entry(4, new Circuit(List.of(COMPTABLE, ORDONNATEUR), List.of(Set.of(TypeVisa.RECEPTIONNAIRE)), true, COMPTA)),
            Map.entry(5, new Circuit(List.of(COMPTABLE), List.of(CBM_CRF, Set.of(TypeVisa.DGB)), true, ORDO)),
            Map.entry(6, new Circuit(List.of(COMPTABLE), List.of(), true, ORDO)),
            Map.entry(7, new Circuit(List.of(COMPTABLE, DETENTEUR), List.of(), true, COMPTA)),
            Map.entry(8, new Circuit(List.of(COMPTABLE, ORDONNATEUR, DETENTEUR), List.of(), true, ORDO)),
            Map.entry(13, new Circuit(List.of(COMPTABLE, DETENTEUR), List.of(), false, COMPTA)),
            Map.entry(20, new Circuit(List.of(COMPTABLE, ORDONNATEUR), List.of(Set.of(TypeVisa.INSPECTEUR_VERIFICATEUR)), true, ORDO)),
            Map.entry(21, new Circuit(List.of(ORDONNATEUR), List.of(CBM_CRF), true, ORDO)),
            Map.entry(24, new Circuit(List.of(COMPTABLE, ORDONNATEUR), List.of(), true, ORDO)),
            Map.entry(25, new Circuit(List.of(COMPTABLE), List.of(), true, Set.of(Role.COMPTABLE_ORDRE, Role.ORDONNATEUR_PRINCIPAL))),
            Map.entry(26, new Circuit(List.of(ORDONNATEUR), List.of(), true, ORDO)));

    /** Modèles dont la commission doit signer (réception au-delà du seuil, réforme, vente/destruction, recensement). */
    public static final Set<Integer> MODELES_COMMISSION = Set.of(5, 6, 20);

    private Circuits() {
    }

    public static Circuit pour(int modele) {
        return CIRCUITS.getOrDefault(modele, new Circuit(List.of(), List.of(), false, Set.of()));
    }

    public static boolean signatureCommission(RoleSignature r) {
        return r == PRESIDENT_COMMISSION || r == SUPPLEANT_COMMISSION || r == MEMBRE_COMMISSION;
    }
}
