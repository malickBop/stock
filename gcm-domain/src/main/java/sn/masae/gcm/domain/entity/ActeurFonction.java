package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Nomination d'un acteur à une fonction réglementaire dans une structure, pour une période donnée. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "acteur_fonction")
public class ActeurFonction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "acteur_id")
    private Acteur acteur;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private FonctionActeur fonction;
    @Column(name = "reference_acte", length = 150)
    private String referenceActe;
    @Column(name = "date_nomination")
    private LocalDate dateNomination;
    @Column(name = "date_accreditation")
    private LocalDate dateAccreditation;
    @Column(name = "date_fin")
    private LocalDate dateFin;
    @Column(nullable = false)
    private boolean actif = true;
}
