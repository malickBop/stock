package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Article / bien suivi dans l'existant d'une structure. Les quantités sont ventilées par situation :
 * en attente d'affectation, en service (affecté à un détenteur), en sortie provisoire, proposé à la réforme.
 * L'existant comptable = attente + service + sortie provisoire + réforme proposée.
 */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "bien")
public class Bien extends BaseEntity {
    @Column(name = "numero_inventaire", nullable = false, unique = true, length = 60)
    private String numeroInventaire;
    @Column(nullable = false, length = 255)
    private String designation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compte_id")
    private CompteNomenclature compte;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @Column(nullable = false)
    private Integer groupe;
    @Column(name = "nature_unite", nullable = false, length = 50)
    private String natureUnite;
    @Column(name = "prix_unitaire", nullable = false, precision = 18, scale = 2)
    private BigDecimal prixUnitaire = BigDecimal.ZERO;
    @Column(name = "qte_attente", nullable = false, precision = 18, scale = 3)
    private BigDecimal qteAttente = BigDecimal.ZERO;
    @Column(name = "qte_service", nullable = false, precision = 18, scale = 3)
    private BigDecimal qteService = BigDecimal.ZERO;
    @Column(name = "qte_sortie_provisoire", nullable = false, precision = 18, scale = 3)
    private BigDecimal qteSortieProvisoire = BigDecimal.ZERO;
    @Column(name = "qte_reforme", nullable = false, precision = 18, scale = 3)
    private BigDecimal qteReforme = BigDecimal.ZERO;
    @Column(name = "seuil_alerte", precision = 18, scale = 3)
    private BigDecimal seuilAlerte;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "detenteur_id")
    private Acteur detenteur;
    @Column(length = 255)
    private String localisation;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private EtatBien etat;
    @Column(length = 100)
    private String marque;
    @Column(name = "numero_serie", length = 100)
    private String numeroSerie;
    @Column(name = "date_acquisition")
    private LocalDate dateAcquisition;

    @Transient
    public BigDecimal getExistant() {
        return qteAttente.add(qteService).add(qteSortieProvisoire).add(qteReforme);
    }

    @Transient
    public BigDecimal getValeurExistant() {
        return getExistant().multiply(prixUnitaire);
    }
}
