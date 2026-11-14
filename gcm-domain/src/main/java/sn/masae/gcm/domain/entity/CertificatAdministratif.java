package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Certificat administratif (modèle 21) autorisant une sortie pour perte, vol, destruction ou consommation. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "certificat_administratif")
public class CertificatAdministratif extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ObjetCertificat objet;
    @Column(nullable = false, length = 1000)
    private String motif;
    @Column(name = "date_certificat", nullable = false)
    private LocalDate dateCertificat;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StatutOperation statut = StatutOperation.BROUILLON;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mouvement_id")
    private Mouvement mouvement;
}
