package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Ligne d'un mouvement : un bien, une quantité, un prix. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "ligne_mouvement")
public class LigneMouvement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mouvement_id")
    private Mouvement mouvement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bien_id")
    private Bien bien;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compte_id")
    private CompteNomenclature compte;
    @Column(nullable = false, length = 255)
    private String designation;
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantite;
    @Column(name = "nature_unite", nullable = false, length = 50)
    private String natureUnite;
    @Column(name = "prix_unitaire", nullable = false, precision = 18, scale = 2)
    private BigDecimal prixUnitaire;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal montant;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private EtatBien etat;
    @Column(length = 255)
    private String observation;
}
