package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Membre nominatif d'une commission. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "commission_membre", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"commission_id", "rang"}),
        @UniqueConstraint(columnNames = {"commission_id", "acteur_id"})})
public class CommissionMembre extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "commission_id")
    private Commission commission;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "acteur_id")
    private Acteur acteur;
    @Enumerated(EnumType.STRING) @Column(name = "role_commission", nullable = false, length = 20)
    private RoleCommission roleCommission;
    @Column(nullable = false)
    private Integer rang;
}
