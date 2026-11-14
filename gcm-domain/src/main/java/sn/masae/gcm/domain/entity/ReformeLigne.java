package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Bien proposé à la réforme. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "reforme_ligne", uniqueConstraints = @UniqueConstraint(columnNames = {"reforme_id", "bien_id"}))
public class ReformeLigne extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reforme_id")
    private Reforme reforme;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bien_id")
    private Bien bien;
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantite;
    @Column(name = "valeur_unitaire", nullable = false, precision = 18, scale = 2)
    private BigDecimal valeurUnitaire;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private EtatBien etat;
    @Column(length = 255)
    private String motif;
}
