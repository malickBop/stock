package sn.masae.gcm.api.workflow;

import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.enums.RoleSignature;
import sn.masae.gcm.domain.enums.TypeVisa;

import java.util.List;
import java.util.Set;

/**
 * Définition du circuit hybride d'un modèle réglementaire.
 *
 * @param signaturesInternes qualités devant signer avant transmission (la commission est ajoutée si requise)
 * @param visasExternes      groupes de visas externes : chaque groupe doit être satisfait par l'un de ses types (ex. CBM ou CRF)
 * @param scanRequis         le document papier signé/visé doit être scanné avant validation finale
 * @param validateurs        rôles habilités à la validation finale
 */
public record Circuit(List<RoleSignature> signaturesInternes, List<Set<TypeVisa>> visasExternes, boolean scanRequis, Set<Role> validateurs) {

    public boolean avecCircuit() {
        return !validateurs.isEmpty();
    }
}
