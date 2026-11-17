package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Report à nouveau : existant d'un bien au 1er janvier de l'exercice. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "solde_ouverture", uniqueConstraints = @UniqueConstraint(columnNames = {"exercice_id", "bien_id"}))
public class SoldeOuverture extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bien_id")
    private Bien bien;
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantite;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal montant;
}
