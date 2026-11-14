package sn.masae.gcm.common.exception;

import java.util.List;

/**
 * Violation d'une règle d'incompatibilité de fonctions (art. 18, 29, 32, 36 de l'Instruction n°0001/MFB/DGB).
 */
public class IncompatibiliteException extends BusinessException {

    public IncompatibiliteException(String message, List<String> details) {
        super(ErrorCode.INCOMPATIBILITE_FONCTIONS, message, details);
    }

    public IncompatibiliteException(String message) {
        this(message, List.of());
    }
}
