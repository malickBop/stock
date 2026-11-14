package sn.masae.gcm.common.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String entite, Object id) {
        super(ErrorCode.RESSOURCE_INTROUVABLE, entite + " introuvable : " + id);
    }
}
