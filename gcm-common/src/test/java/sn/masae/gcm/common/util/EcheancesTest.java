package sn.masae.gcm.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EcheancesTest {

    @Test
    void echeanceEst30JuinN1() {
        assertThat(Echeances.echeanceReddition(2026)).isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test
    void statutSelonDate() {
        assertThat(Echeances.statut(2026, LocalDate.of(2027, 1, 1))).isEqualTo(Echeances.StatutEcheance.DANS_LES_DELAIS);
        assertThat(Echeances.statut(2026, LocalDate.of(2027, 6, 15))).isEqualTo(Echeances.StatutEcheance.A_PREPARER);
        assertThat(Echeances.statut(2026, LocalDate.of(2027, 7, 1))).isEqualTo(Echeances.StatutEcheance.EN_RETARD);
    }

    @Test
    void amendeParMoisDeRetard() {
        assertThat(Echeances.amendeMaximale(2026, LocalDate.of(2027, 6, 30))).isZero();
        assertThat(Echeances.amendeMaximale(2026, LocalDate.of(2027, 7, 2))).isEqualTo(50_000L);
        assertThat(Echeances.amendeMaximale(2026, LocalDate.of(2027, 9, 1))).isEqualTo(150_000L);
    }

    @Test
    void formatFcfa() {
        assertThat(Montants.fcfa(new BigDecimal("1360000"))).isEqualTo("1 360 000");
        assertThat(Montants.montant(new BigDecimal("4"), new BigDecimal("75000"))).isEqualByComparingTo("300000");
    }
}
