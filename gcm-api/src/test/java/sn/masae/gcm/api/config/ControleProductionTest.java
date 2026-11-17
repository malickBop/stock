package sn.masae.gcm.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControleProductionTest {

    private static final String SECRET = "x".repeat(64);

    private static GcmProperties props(boolean prod, String secret, String demo, String admin) {
        return new GcmProperties(secret, null, null, null, null, demo, null, admin, prod);
    }

    @Test
    void developpementSansControle() {
        assertThat(ControleProduction.anomalies(props(false, ControleProduction.SECRET_DEV, "Masae@2026", null), "gcm")).isEmpty();
    }

    @Test
    void productionConforme() {
        assertThat(ControleProduction.anomalies(props(true, SECRET, "", "Admin-Initial-2026!"), "mot-de-passe-solide")).isEmpty();
    }

    @Test
    void productionRefuseLesValeursDeDeveloppement() {
        assertThat(ControleProduction.anomalies(props(true, ControleProduction.SECRET_DEV, "Masae@2026", "Masae@2026"), "gcm"))
                .hasSize(4)
                .anySatisfy(m -> assertThat(m).contains("GCM_JWT_SECRET"))
                .anySatisfy(m -> assertThat(m).contains("GCM_DEMO_PASSWORD"))
                .anySatisfy(m -> assertThat(m).contains("GCM_ADMIN_PASSWORD"))
                .anySatisfy(m -> assertThat(m).contains("POSTGRES_PASSWORD"));
    }
}
