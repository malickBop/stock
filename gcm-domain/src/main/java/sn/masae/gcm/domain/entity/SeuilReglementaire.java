package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Seuils réglementaires paramétrables (ex. réception par commission, art. 44-45). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "seuil_reglementaire")
public class SeuilReglementaire extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 255)
    private String libelle;
    @Enumerated(EnumType.STRING) @Column(name = "type_service", length = 20)
    private TypeService typeService;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal montant;
    @Column(name = "reference_texte", length = 150)
    private String referenceTexte;
}
