package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Écriture du livre-journal des matières (modèle 11) : numérotation ininterrompue, non modifiable. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "ecriture_journal", uniqueConstraints = @UniqueConstraint(columnNames = {"structure_id", "exercice_id", "numero_ordre"}))
public class EcritureJournal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Column(name = "numero_ordre", nullable = false)
    private Long numeroOrdre;
    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mouvement_id")
    private Mouvement mouvement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ligne_mouvement_id")
    private LigneMouvement ligneMouvement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compte_id")
    private CompteNomenclature compte;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bien_id")
    private Bien bien;
    @Column(nullable = false, length = 255)
    private String libelle;
    @Column(name = "reference_piece", length = 150)
    private String referencePiece;
    @Column(name = "quantite_entree", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantiteEntree = BigDecimal.ZERO;
    @Column(name = "quantite_sortie", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantiteSortie = BigDecimal.ZERO;
    @Column(name = "montant_entree", nullable = false, precision = 18, scale = 2)
    private BigDecimal montantEntree = BigDecimal.ZERO;
    @Column(name = "montant_sortie", nullable = false, precision = 18, scale = 2)
    private BigDecimal montantSortie = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
