package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Visa externe (CBM, CRF, DGB, vérificateur, réceptionnaire) apposé sur papier puis justifié par scan. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "document_visa_externe", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "type_visa"}))
public class DocumentVisaExterne extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id")
    private DocumentReglementaire document;
    @Enumerated(EnumType.STRING) @Column(name = "type_visa", nullable = false, length = 30)
    private TypeVisa typeVisa;
    @Column(name = "autorite", length = 200)
    private String autorite;
    @Column(name = "reference_visa", length = 150)
    private String referenceVisa;
    @Column(name = "date_visa", nullable = false)
    private LocalDate dateVisa;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "piece_jointe_id")
    private PieceJointe pieceJointe;
}
