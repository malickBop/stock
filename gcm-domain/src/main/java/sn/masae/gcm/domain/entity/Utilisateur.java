package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Compte utilisateur de la plateforme ; le périmètre est la structure de rattachement et ses descendantes. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "utilisateur")
public class Utilisateur extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String login;
    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;
    @Column(name = "nom_complet", nullable = false, length = 200)
    private String nomComplet;
    @Column(length = 150)
    private String email;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "structure_id")
    private Structure structure;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "acteur_id")
    private Acteur acteur;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utilisateur_role", joinColumns = @JoinColumn(name = "utilisateur_id"))
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 40)
    private Set<Role> roles = new HashSet<>();
    @Column(nullable = false)
    private boolean actif = true;
    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;
}
