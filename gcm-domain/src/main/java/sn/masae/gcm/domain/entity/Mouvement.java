package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Mouvement de matières (entrée, sortie, retour, affectation, mutation…) adossé à une pièce justificative. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "mouvement", uniqueConstraints = @UniqueConstraint(columnNames = {"structure_id", "exercice_id", "numero"}))
public class Mouvement extends BaseEntity {
    @Column(nullable = false, length = 40)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Enumerated(EnumType.STRING) @Column(name = "type_mouvement", nullable = false, length = 30)
    private TypeMouvement typeMouvement;
    @Enumerated(EnumType.STRING) @Column(name = "sous_type", nullable = false, length = 30)
    private SousTypeMouvement sousType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutMouvement statut = StatutMouvement.BROUILLON;
    @Column(name = "date_mouvement", nullable = false)
    private LocalDate dateMouvement;
    @Column(name = "reference_piece", length = 150)
    private String referencePiece;
    @Column(length = 255)
    private String tiers;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "structure_destination_id")
    private Structure structureDestination;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "detenteur_source_id")
    private Acteur detenteurSource;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "detenteur_destination_id")
    private Acteur detenteurDestination;
    @Column(name = "receptionnaire", length = 200)
    private String receptionnaire;
    @Column(name = "date_retour_prevue")
    private LocalDate dateRetourPrevue;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mouvement_origine_id")
    private Mouvement mouvementOrigine;
    @Column(name = "date_validation")
    private Instant dateValidation;
    @Column(columnDefinition = "text")
    private String observations;
    @OneToMany(mappedBy = "mouvement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LigneMouvement> lignes = new ArrayList<>();

    @Transient
    public BigDecimal getMontantTotal() {
        return lignes.stream().map(LigneMouvement::getMontant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addLigne(LigneMouvement l) {
        l.setMouvement(this);
        lignes.add(l);
    }
}
