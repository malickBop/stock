package sn.masae.gcm.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Client applicatif externe (SIGIF, SYSBUDGEP…) authentifié par clé d'API. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "api_client")
public class ApiClient extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String nom;
    @Column(name = "cle_hash", nullable = false, unique = true, length = 100)
    private String cleHash;
    @Column(length = 255)
    private String description;
    @Column(nullable = false)
    private boolean actif = true;
}
