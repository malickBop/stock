package sn.masae.gcm.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres applicatifs (préfixe {@code gcm}).
 *
 * @param jwtSecret       secret HMAC (≥ 32 octets) de signature des jetons
 * @param jwtValiditeMinutes durée de validité d'un jeton
 * @param stockageDir     répertoire de la GED (scans, pièces justificatives)
 * @param retentionAnnees durée légale de conservation des pièces
 * @param seed            import automatique des référentiels au démarrage
 * @param demoPassword    mot de passe initial des comptes de démonstration (vide : aucun compte de démonstration)
 * @param adminPassword   mot de passe initial du compte {@code admin} créé s'il n'existe pas (production)
 * @param production      active les contrôles bloquants de configuration au démarrage (profil {@code prod})
 */
@ConfigurationProperties(prefix = "gcm")
public record GcmProperties(String jwtSecret, Integer jwtValiditeMinutes, String stockageDir, Integer retentionAnnees,
                            Boolean seed, String demoPassword, Long tailleMaxScanOctets, String adminPassword, Boolean production) {

    public GcmProperties {
        if (jwtValiditeMinutes == null) jwtValiditeMinutes = 480;
        if (retentionAnnees == null) retentionAnnees = 10;
        if (seed == null) seed = Boolean.TRUE;
        if (production == null) production = Boolean.FALSE;
        if (stockageDir == null) stockageDir = "./data/ged";
        if (tailleMaxScanOctets == null) tailleMaxScanOctets = 20L * 1024 * 1024;
    }
}
