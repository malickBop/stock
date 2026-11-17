package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Instance d'un des 26 modèles réglementaires, suivie dans le circuit hybride de validation. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "document_reglementaire")
public class DocumentReglementaire extends BaseEntity {
    @Column(nullable = false)
    private Integer modele;
    @Column(nullable = false, unique = true, length = 60)
    private String numero;
    @Column(nullable = false, length = 255)
    private String titre;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private StatutWorkflow statut = StatutWorkflow.BROUILLON;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mouvement_id")
    private Mouvement mouvement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recensement_id")
    private Recensement recensement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reforme_id")
    private Reforme reforme;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "certificat_id")
    private CertificatAdministratif certificat;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_parent_id")
    private DocumentReglementaire documentParent;
    @Column(name = "requiert_commission", nullable = false)
    private boolean requiertCommission;
    @Column(name = "montant_total", precision = 18, scale = 2)
    private BigDecimal montantTotal;
    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion = 1;
    @Column(name = "date_validation_finale")
    private Instant dateValidationFinale;
    @Column(name = "valide_par", length = 100)
    private String validePar;
    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DocumentSignature> signatures = new ArrayList<>();
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DocumentVisaExterne> visas = new ArrayList<>();
}
