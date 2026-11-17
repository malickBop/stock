package sn.masae.gcm.documents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gabarit d'un modèle, extrait des onglets Modèle_01 à Modèle_26 du classeur de référence
 * ({@code modeles/specs.json}) : intitulé, articles, zones d'en-tête, colonnes et mentions réglementaires.
 */
public record SpecModele(String titre, String articles, List<String> champs, List<String> colonnes, List<String> mentions, boolean entete) {

    private static final Map<Integer, SpecModele> SPECS = charger();

    public static SpecModele de(int numero) {
        SpecModele s = SPECS.get(numero);
        if (s == null) throw new IllegalArgumentException("Modèle inconnu : " + numero);
        return s;
    }

    public static Map<Integer, SpecModele> tous() {
        return SPECS;
    }

    private static Map<Integer, SpecModele> charger() {
        try (InputStream in = SpecModele.class.getResourceAsStream("/modeles/specs.json")) {
            Map<String, SpecModele> m = new ObjectMapper().readValue(in, new TypeReference<>() {
            });
            Map<Integer, SpecModele> r = new TreeMap<>();
            m.forEach((k, v) -> r.put(Integer.valueOf(k), v));
            return Map.copyOf(r);
        } catch (IOException e) {
            throw new UncheckedIOException("Gabarits des modèles introuvables", e);
        }
    }
}
