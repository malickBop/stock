package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Lien de parenté ou d'alliance déclaré entre deux acteurs (contrôle d'incompatibilité, art. 29). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "lien_parente")
public class LienParente extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "acteur_id")
    private Acteur acteur;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "parent_acteur_id")
    private Acteur parentActeur;
    @Column(name = "nature_lien", nullable = false, length = 100)
    private String natureLien;
}
