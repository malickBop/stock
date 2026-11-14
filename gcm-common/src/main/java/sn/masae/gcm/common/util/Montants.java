package sn.masae.gcm.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utilitaires de calcul et de formatage des montants en FCFA (pas de décimales).
 */
public final class Montants {

    private Montants() {
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal montant(BigDecimal quantite, BigDecimal prixUnitaire) {
        return nz(quantite).multiply(nz(prixUnitaire)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Format « 1 360 000 » conforme aux états GCM. */
    public static String fcfa(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        DecimalFormat f = new DecimalFormat("#,##0", s);
        return f.format(nz(v).setScale(0, RoundingMode.HALF_UP));
    }

    public static String quantite(BigDecimal v) {
        BigDecimal q = nz(v).stripTrailingZeros();
        return q.scale() <= 0 ? fcfa(q) : q.toPlainString().replace('.', ',');
    }
}
