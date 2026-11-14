package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Exercice budgétaire (année civile). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "exercice")
public class Exercice extends BaseEntity {
    @Column(nullable = false, unique = true)
    private Integer annee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutExercice statut = StatutExercice.OUVERT;
    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    public boolean isCloture() {
        return statut == StatutExercice.CLOTURE;
    }
}
