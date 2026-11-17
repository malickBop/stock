package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Commission annuelle d'une structure (réception, recensement, réforme), composée de quatre membres nominatifs. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "commission", uniqueConstraints = @UniqueConstraint(columnNames = {"structure_id", "exercice_id"}))
public class Commission extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Column(name = "reference_decision", length = 150)
    private String referenceDecision;
    @Column(name = "date_decision")
    private LocalDate dateDecision;
    @OneToMany(mappedBy = "commission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rang ASC")
    private List<CommissionMembre> membres = new ArrayList<>();
}
