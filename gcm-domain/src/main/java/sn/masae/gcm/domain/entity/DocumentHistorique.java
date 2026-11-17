package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Historique immuable des transitions d'état d'un document. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "document_historique")
public class DocumentHistorique {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id")
    private DocumentReglementaire document;
    @Enumerated(EnumType.STRING) @Column(name = "ancien_statut", length = 30)
    private StatutWorkflow ancienStatut;
    @Enumerated(EnumType.STRING) @Column(name = "nouveau_statut", nullable = false, length = 30)
    private StatutWorkflow nouveauStatut;
    @Column(nullable = false, length = 50)
    private String action;
    @Column(name = "utilisateur_login", length = 100)
    private String utilisateurLogin;
    @Column(length = 1000)
    private String commentaire;
    @Column(name = "date_action", nullable = false)
    private Instant dateAction = Instant.now();
}
