package sn.masae.gcm.api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.masae.gcm.domain.entity.AuditLog;
import sn.masae.gcm.domain.repository.AuditLogRepository;

/**
 * Traçabilité : chaque opération sensible est inscrite dans le journal d'audit (table en ajout seul).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tracer(String action, String entite, Long entiteId, String structureCode, String details) {
        tracerAs(loginCourant(), action, entite, entiteId, structureCode, details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void tracerAs(String login, String action, String entite, Long entiteId, String structureCode, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntite(entite);
        log.setEntiteId(entiteId);
        log.setStructureCode(structureCode);
        log.setDetails(details);
        log.setUtilisateurLogin(login);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            log.setAdresseIp(attrs.getRequest().getRemoteAddr());
        }
        repository.save(log);
    }

    public static String loginCourant() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "systeme" : a.getName();
    }
}
