package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Propriété bâtie, non bâtie ou bâtiment pris à bail (modèles 16, 17, 18). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "immeuble")
public class Immeuble extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String reference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @Enumerated(EnumType.STRING) @Column(name = "type_immeuble", nullable = false, length = 20)
    private TypeImmeuble typeImmeuble;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "compte_id")
    private CompteNomenclature compte;
    @Column(nullable = false, length = 255)
    private String designation;
    @Column(nullable = false, length = 255)
    private String localisation;
    @Column(name = "titre_foncier", length = 100)
    private String titreFoncier;
    @Column(precision = 18, scale = 2)
    private BigDecimal superficie;
    @Column(name = "superficie_batie", precision = 18, scale = 2)
    private BigDecimal superficieBatie;
    @Column(name = "nombre_pieces")
    private Integer nombrePieces;
    @Column(name = "valeur", precision = 18, scale = 2)
    private BigDecimal valeur;
    @Column(name = "date_acquisition")
    private LocalDate dateAcquisition;
    @Column(name = "mode_acquisition", length = 100)
    private String modeAcquisition;
    @Column(name = "affectation", length = 255)
    private String affectation;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private EtatBien etat;
    @Column(length = 200)
    private String bailleur;
    @Column(name = "loyer_mensuel", precision = 18, scale = 2)
    private BigDecimal loyerMensuel;
    @Column(name = "date_debut_bail")
    private LocalDate dateDebutBail;
    @Column(name = "date_fin_bail")
    private LocalDate dateFinBail;
    @Column(length = 500)
    private String observations;
}
