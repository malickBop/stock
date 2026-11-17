package sn.masae.gcm.api.workflow;

import java.util.Map;

/** Intitulés officiels des 26 modèles (onglets Modèle_01 à Modèle_26). */
public final class ModelesReglementaires {

    public static final Map<Integer, String> TITRES = Map.ofEntries(
            Map.entry(1, "Procès-verbal de réception"),
            Map.entry(2, "Bon d'entrée dans l'existant"),
            Map.entry(3, "Bon de sortie définitive de l'existant"),
            Map.entry(4, "Bon de sortie provisoire"),
            Map.entry(5, "Procès-verbal de réforme des matières"),
            Map.entry(6, "Procès-verbal de vente / destruction"),
            Map.entry(7, "Bordereau d'affectation"),
            Map.entry(8, "Bordereau de mutation des matières"),
            Map.entry(9, "Fiche de stock"),
            Map.entry(10, "Fichier des matières tenu par le magasinier"),
            Map.entry(11, "Livre-journal des matières"),
            Map.entry(12, "Grand-livre des comptes"),
            Map.entry(13, "Fiche d'inventaire individuel contradictoire"),
            Map.entry(14, "Fiche de détenteur des matières"),
            Map.entry(15, "Fiche de consommation des stocks"),
            Map.entry(16, "Fiche des propriétés bâties"),
            Map.entry(17, "Fiche des propriétés non bâties"),
            Map.entry(18, "Fiche des bâtiments pris en bail"),
            Map.entry(19, "Relevé récapitulatif des matières"),
            Map.entry(20, "Procès-verbal de recensement"),
            Map.entry(21, "Certificat administratif portant autorisation"),
            Map.entry(22, "Balance générale des comptes"),
            Map.entry(23, "Bordereau de centralisation des procès-verbaux de recensement"),
            Map.entry(24, "Compte de gestion"),
            Map.entry(25, "Compte central des matières de l'État"),
            Map.entry(26, "Compte administratif"));

    private ModelesReglementaires() {
    }

    public static String titre(int modele) {
        return TITRES.getOrDefault(modele, "Modèle " + modele);
    }
}
