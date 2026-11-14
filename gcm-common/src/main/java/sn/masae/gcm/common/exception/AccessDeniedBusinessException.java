package sn.masae.gcm.common.exception;

/**
 * Accès refusé pour raison de rôle ou de périmètre hiérarchique.
 */
public class AccessDeniedBusinessException extends BusinessException {

    public AccessDeniedBusinessException(String message) {
        super(ErrorCode.ACCES_REFUSE, message);
    }
}
