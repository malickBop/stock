package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Opération de recensement / inventaire contradictoire (modèles 13, 20, 23). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "recensement")
public class Recensement extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;
    @Column(name = "date_fin")
    private LocalDate dateFin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutOperation statut = StatutOperation.BROUILLON;
    @Column(columnDefinition = "text")
    private String observations;
    @OneToMany(mappedBy = "recensement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RecensementLigne> lignes = new ArrayList<>();
}
