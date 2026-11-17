package sn.masae.gcm.common.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Échéances réglementaires de reddition des comptes (art. 79 : 30 juin N+1).
 */
public final class Echeances {

    public static final int JOURS_PREPARATION = 30;

    private Echeances() {
    }

    public static LocalDate echeanceReddition(int exercice) {
        return LocalDate.of(exercice + 1, 6, 30);
    }

    public static StatutEcheance statut(int exercice, LocalDate aujourdHui) {
        LocalDate limite = echeanceReddition(exercice);
        if (aujourdHui.isAfter(limite)) {
            return StatutEcheance.EN_RETARD;
        }
        if (aujourdHui.isAfter(limite.minusDays(JOURS_PREPARATION))) {
            return StatutEcheance.A_PREPARER;
        }
        return StatutEcheance.DANS_LES_DELAIS;
    }

    public static long joursRestants(int exercice, LocalDate aujourdHui) {
        return ChronoUnit.DAYS.between(aujourdHui, echeanceReddition(exercice));
    }

    /** Amende indicative art. 79.b : 50 000 FCFA maximum par mois de retard entamé. */
    public static long amendeMaximale(int exercice, LocalDate aujourdHui) {
        LocalDate limite = echeanceReddition(exercice);
        if (!aujourdHui.isAfter(limite)) {
            return 0;
        }
        long mois = ChronoUnit.MONTHS.between(limite, aujourdHui) + 1;
        return mois * 50_000L;
    }

    public enum StatutEcheance { DANS_LES_DELAIS, A_PREPARER, EN_RETARD }
}
