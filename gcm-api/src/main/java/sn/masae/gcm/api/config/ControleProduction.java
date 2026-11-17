package sn.masae.gcm.api.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Refuse le démarrage en production (gcm.production=true) si la configuration reprend des valeurs de développement. */
@Component
public class ControleProduction {

    static final String SECRET_DEV = "changer-ce-secret-en-production-minimum-32-octets";
    static final String MOT_DE_PASSE_DEMO = "Masae@2026";

    public ControleProduction(GcmProperties props, Environment env) {
        List<String> erreurs = anomalies(props, env.getProperty("spring.datasource.password"));
        if (!erreurs.isEmpty()) {
            throw new IllegalStateException("Configuration de production refusée : " + String.join(" ; ", erreurs));
        }
    }

    static List<String> anomalies(GcmProperties p, String motDePasseBase) {
        List<String> e = new ArrayList<>();
        if (!p.production()) return e;
        String secret = p.jwtSecret() == null ? "" : p.jwtSecret();
        if (secret.equals(SECRET_DEV) || secret.getBytes(StandardCharsets.UTF_8).length < 48) {
            e.add("GCM_JWT_SECRET doit être un secret propre d'au moins 48 octets (openssl rand -base64 48)");
        }
        if (p.demoPassword() != null && !p.demoPassword().isBlank()) {
            e.add("GCM_DEMO_PASSWORD doit être vide : pas de comptes de démonstration en production");
        }
        if (p.adminPassword() != null && !p.adminPassword().isBlank()
                && (p.adminPassword().length() < 12 || p.adminPassword().equals(MOT_DE_PASSE_DEMO))) {
            e.add("GCM_ADMIN_PASSWORD doit contenir au moins 12 caractères et différer du mot de passe de démonstration");
        }
        if (motDePasseBase == null || motDePasseBase.length() < 12 || motDePasseBase.equals("gcm")) {
            e.add("POSTGRES_PASSWORD doit contenir au moins 12 caractères");
        }
        return e;
    }
}
