package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Proposition / opération de réforme (modèles 5 et 6). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "reforme")
public class Reforme extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Column(name = "date_proposition", nullable = false)
    private LocalDate dateProposition;
    @Column(nullable = false, length = 500)
    private String motif;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutOperation statut = StatutOperation.BROUILLON;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private DecisionReforme decision;
    @Column(name = "date_execution")
    private LocalDate dateExecution;
    @Column(name = "produit_vente", precision = 18, scale = 2)
    private BigDecimal produitVente;
    @Column(name = "reference_quittance", length = 150)
    private String referenceQuittance;
    @OneToMany(mappedBy = "reforme", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReformeLigne> lignes = new ArrayList<>();
}
