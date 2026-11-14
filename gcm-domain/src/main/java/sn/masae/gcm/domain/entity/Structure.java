package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Structure administrative du MASAE (STR-001 DAGE … STR-026), organisée en hiérarchie. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "structure")
public class Structure extends BaseEntity {
    @Column(nullable = false, unique = true, length = 20)
    private String code;
    @Column(nullable = false, length = 255)
    private String libelle;
    @Column(length = 50)
    private String sigle;
    @Enumerated(EnumType.STRING) @Column(name = "type_structure", nullable = false, length = 20)
    private TypeStructure typeStructure;
    @Enumerated(EnumType.STRING) @Column(name = "type_service", nullable = false, length = 20)
    private TypeService typeService;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private Structure parent;
    @Column(length = 100)
    private String region;
    @Column(length = 100)
    private String departement;
    @Column(length = 255)
    private String adresse;
    @Column(name = "code_ordonnateur", length = 50)
    private String codeOrdonnateur;
    @Column(nullable = false)
    private boolean active = true;
}
