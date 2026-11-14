package sn.masae.gcm.common.exception;

import java.util.Collections;
import java.util.List;

/**
 * Exception métier bloquante : la règle de gestion n'est pas respectée, l'opération est refusée.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final List<String> details;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, Collections.emptyList());
    }

    public BusinessException(ErrorCode code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Collections.emptyList() : List.copyOf(details);
    }

    public BusinessException(String message) {
        this(ErrorCode.REGLE_METIER, message);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
