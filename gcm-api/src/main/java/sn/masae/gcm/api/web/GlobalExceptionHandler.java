package sn.masae.gcm.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;

import java.util.List;

/**
 * Traduction des exceptions en réponses HTTP explicites (en français).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErreurResponse> metier(BusinessException e) {
        HttpStatus status = switch (e.getCode()) {
            case RESSOURCE_INTROUVABLE -> HttpStatus.NOT_FOUND;
            case ACCES_REFUSE -> HttpStatus.FORBIDDEN;
            case CONFLIT -> HttpStatus.CONFLICT;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(ErreurResponse.of(e.getCode().name(), e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurResponse> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(ErreurResponse.of(ErrorCode.VALIDATION.name(), "Données invalides", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurResponse> acces(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErreurResponse.of(ErrorCode.ACCES_REFUSE.name(), "Accès refusé", null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErreurResponse> verrou(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErreurResponse.of(ErrorCode.CONFLIT.name(),
                "L'enregistrement a été modifié par un autre utilisateur, veuillez recharger", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurResponse> integrite(DataIntegrityViolationException e) {
        LOG.warn("Violation d'intégrité : {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErreurResponse.of(ErrorCode.CONFLIT.name(),
                "Contrainte d'intégrité violée", List.of(e.getMostSpecificCause().getMessage())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErreurResponse> taille(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErreurResponse.of(ErrorCode.VALIDATION.name(), "Fichier trop volumineux", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErreurResponse> argument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErreurResponse.of(ErrorCode.VALIDATION.name(), e.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErreurResponse> methode(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErreurResponse.of(ErrorCode.VALIDATION.name(), "Méthode " + e.getMethod() + " non autorisée", null));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErreurResponse> introuvable(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErreurResponse.of(ErrorCode.RESSOURCE_INTROUVABLE.name(), "Ressource introuvable : /" + e.getResourcePath(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurResponse> autre(Exception e) {
        LOG.error("Erreur inattendue", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErreurResponse.of("ERREUR_INTERNE",
                "Erreur interne, veuillez contacter l'administrateur", null));
    }
}
