package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Ligne de recensement : quantité théorique (comptable) vs quantité physique constatée. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "recensement_ligne", uniqueConstraints = @UniqueConstraint(columnNames = {"recensement_id", "bien_id"}))
public class RecensementLigne extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recensement_id")
    private Recensement recensement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bien_id")
    private Bien bien;
    @Column(name = "qte_theorique", nullable = false, precision = 18, scale = 3)
    private BigDecimal qteTheorique;
    @Column(name = "qte_physique", precision = 18, scale = 3)
    private BigDecimal qtePhysique;
    @Column(name = "prix_unitaire", nullable = false, precision = 18, scale = 2)
    private BigDecimal prixUnitaire;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private EtatBien etat;
    @Column(length = 255)
    private String observation;

    @Transient
    public BigDecimal getEcart() {
        return qtePhysique == null ? BigDecimal.ZERO : qtePhysique.subtract(qteTheorique);
    }
}
