package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Suivi du dépôt des documents de reddition (comptes de gestion, central, administratif). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "reddition", uniqueConstraints = @UniqueConstraint(columnNames = {"structure_id", "exercice_id", "type_reddition"}))
public class Reddition extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Enumerated(EnumType.STRING) @Column(name = "type_reddition", nullable = false, length = 30)
    private TypeReddition typeReddition;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutOperation statut = StatutOperation.BROUILLON;
    @Column(name = "date_depot")
    private LocalDate dateDepot;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_id")
    private DocumentReglementaire document;
}
