package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Signature interne (électronique ou attestée) apposée sur un document. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "document_signature", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "role_signature", "acteur_id"}))
public class DocumentSignature extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id")
    private DocumentReglementaire document;
    @Enumerated(EnumType.STRING) @Column(name = "role_signature", nullable = false, length = 30)
    private RoleSignature roleSignature;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "acteur_id")
    private Acteur acteur;
    @Column(name = "signataire_nom", nullable = false, length = 200)
    private String signataireNom;
    @Column(name = "date_signature", nullable = false)
    private Instant dateSignature;
    @Column(name = "utilisateur_login", length = 100)
    private String utilisateurLogin;
}
