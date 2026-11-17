package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Pièce de la GED (scan signé, facture…) avec empreinte SHA-256 et durée de rétention (10 ans). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "piece_jointe")
public class PieceJointe extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_id")
    private DocumentReglementaire document;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mouvement_id")
    private Mouvement mouvement;
    @Enumerated(EnumType.STRING) @Column(name = "type_piece", nullable = false, length = 30)
    private TypePieceJointe typePiece;
    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private Long taille;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "chemin_stockage", nullable = false, length = 500)
    private String cheminStockage;
    @Column(name = "date_fin_retention", nullable = false)
    private LocalDate dateFinRetention;
}
