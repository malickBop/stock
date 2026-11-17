package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Journal d'audit immuable de toutes les opérations sensibles. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "date_action", nullable = false)
    private Instant dateAction = Instant.now();
    @Column(name = "utilisateur_login", length = 100)
    private String utilisateurLogin;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(nullable = false, length = 60)
    private String entite;
    @Column(name = "entite_id")
    private Long entiteId;
    @Column(name = "structure_code", length = 20)
    private String structureCode;
    @Column(columnDefinition = "text")
    private String details;
    @Column(name = "adresse_ip", length = 60)
    private String adresseIp;
}
