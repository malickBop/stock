package sn.masae.gcm.documents;

import java.util.List;

/**
 * Contenu pré-rempli d'un modèle réglementaire (Annexe à l'Instruction n°0001/MFB/DGB), indépendant du format de sortie.
 *
 * @param numero          numéro du modèle (1 à 26)
 * @param titre           intitulé officiel
 * @param articles        références d'articles de l'Instruction
 * @param ministere       ministère / organisme public
 * @param structure       service ou établissement émetteur
 * @param reference       numéro de la pièce
 * @param champs          zones d'en-tête « libellé : valeur »
 * @param colonnes        en-têtes du tableau principal
 * @param lignes          lignes du tableau (valeurs déjà formatées)
 * @param totaux          ligne de totaux (même nombre de cellules que les colonnes, cellules vides autorisées) ou {@code null}
 * @param mentions        paragraphes réglementaires (certifications, arrêtés, récépissés, conclusions)
 * @param cadres          cadres de signature et de visa
 * @param filigrane       mention de statut (ex. « BROUILLON ») ou {@code null}
 */
public record DocumentModele(int numero, String titre, String articles, String ministere, String structure, String reference,
                             List<Champ> champs, List<String> colonnes, List<List<String>> lignes, List<String> totaux,
                             List<String> mentions, List<Cadre> cadres, String filigrane) {

    public record Champ(String libelle, String valeur) {
    }

    /**
     * Cadre de signature ou de visa.
     *
     * @param qualite   qualité du signataire (ex. « Le Comptable des matières », « Visa du Contrôleur budgétaire »)
     * @param nom       nom du signataire (pré-rempli si connu)
     * @param mention   date et mode de signature (ex. « Signé le 12/03/2026 ») ou vide pour signature manuscrite
     */
    public record Cadre(String qualite, String nom, String mention) {
    }

    public boolean paysage() {
        return colonnes != null && colonnes.size() > 7;
    }
}
