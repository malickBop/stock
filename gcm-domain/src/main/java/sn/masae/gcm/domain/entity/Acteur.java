package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Personne physique intervenant dans la chaîne de la comptabilité des matières. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "acteur")
public class Acteur extends BaseEntity {
    @Column(length = 50, unique = true)
    private String matricule;
    @Column(nullable = false, length = 100)
    private String nom;
    @Column(length = 150)
    private String prenom;
    @Column(length = 150)
    private String titre;
    @Column(length = 150)
    private String email;
    @Column(length = 50)
    private String telephone;
    @Column(nullable = false)
    private boolean actif = true;

    @Transient
    public String getNomComplet() {
        return ((prenom == null ? "" : prenom + " ") + nom).trim();
    }
}
