package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Compte de la nomenclature des matières : Groupe-Principal-Secondaire-Divisionnaire-Sous-compte. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "compte_nomenclature")
public class CompteNomenclature extends BaseEntity {
    @Column(name = "numero_complet", nullable = false, unique = true, length = 30)
    private String numeroComplet;
    @Column(nullable = false)
    private Integer groupe;
    @Column(name = "compte_principal", nullable = false, length = 4)
    private String comptePrincipal;
    @Column(name = "compte_secondaire", nullable = false, length = 6)
    private String compteSecondaire;
    @Column(name = "compte_divisionnaire", nullable = false, length = 8)
    private String compteDivisionnaire;
    @Column(name = "sous_compte", nullable = false, length = 8)
    private String sousCompte;
    @Column(nullable = false, length = 255)
    private String libelle;
    @Column(name = "nature_unite", length = 50)
    private String natureUnite;
    @Column(nullable = false)
    private boolean actif = true;
}
