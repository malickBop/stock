--liquibase formatted sql

--changeset gcm:002-bien
CREATE TABLE bien (
    id                     BIGSERIAL PRIMARY KEY,
    numero_inventaire      VARCHAR(60)  NOT NULL UNIQUE,
    designation            VARCHAR(255) NOT NULL,
    compte_id              BIGINT NOT NULL REFERENCES compte_nomenclature (id),
    structure_id           BIGINT NOT NULL REFERENCES structure (id),
    groupe                 INTEGER NOT NULL CHECK (groupe IN (1, 2)),
    nature_unite           VARCHAR(50) NOT NULL,
    prix_unitaire          NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (prix_unitaire >= 0),
    qte_attente            NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (qte_attente >= 0),
    qte_service            NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (qte_service >= 0),
    qte_sortie_provisoire  NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (qte_sortie_provisoire >= 0),
    qte_reforme            NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (qte_reforme >= 0),
    seuil_alerte           NUMERIC(18,3) CHECK (seuil_alerte >= 0),
    detenteur_id           BIGINT REFERENCES acteur (id),
    localisation           VARCHAR(255),
    etat                   VARCHAR(20) CHECK (etat IN ('NEUF','BON','PASSABLE','MAUVAIS','HORS_USAGE')),
    marque                 VARCHAR(100),
    numero_serie           VARCHAR(100),
    date_acquisition       DATE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);
CREATE INDEX ix_bien_structure ON bien (structure_id);
CREATE INDEX ix_bien_compte ON bien (compte_id);
CREATE INDEX ix_bien_detenteur ON bien (detenteur_id);

--changeset gcm:002-mouvement
CREATE TABLE mouvement (
    id                         BIGSERIAL PRIMARY KEY,
    numero                     VARCHAR(40) NOT NULL,
    structure_id               BIGINT NOT NULL REFERENCES structure (id),
    exercice_id                BIGINT NOT NULL REFERENCES exercice (id),
    type_mouvement             VARCHAR(30) NOT NULL CHECK (type_mouvement IN ('ENTREE','SORTIE_DEFINITIVE','SORTIE_PROVISOIRE','RETOUR','AFFECTATION','REPRISE','MUTATION')),
    sous_type                  VARCHAR(30) NOT NULL,
    statut                     VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','EN_COURS','VALIDE','ANNULE')),
    date_mouvement             DATE NOT NULL,
    reference_piece            VARCHAR(150),
    tiers                      VARCHAR(255),
    structure_destination_id   BIGINT REFERENCES structure (id),
    detenteur_source_id        BIGINT REFERENCES acteur (id),
    detenteur_destination_id   BIGINT REFERENCES acteur (id),
    receptionnaire             VARCHAR(200),
    date_retour_prevue         DATE,
    mouvement_origine_id       BIGINT REFERENCES mouvement (id),
    date_validation            TIMESTAMPTZ,
    observations               TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_mouvement_numero UNIQUE (structure_id, exercice_id, numero),
    CONSTRAINT ck_mvt_mutation CHECK (type_mouvement <> 'MUTATION' OR (structure_destination_id IS NOT NULL OR detenteur_destination_id IS NOT NULL)),
    CONSTRAINT ck_mvt_retour CHECK (type_mouvement <> 'RETOUR' OR mouvement_origine_id IS NOT NULL),
    CONSTRAINT ck_mvt_sortie_prov CHECK (type_mouvement <> 'SORTIE_PROVISOIRE' OR date_retour_prevue IS NOT NULL)
);
CREATE INDEX ix_mouvement_struct_ex ON mouvement (structure_id, exercice_id);
CREATE INDEX ix_mouvement_origine ON mouvement (mouvement_origine_id);

CREATE TABLE ligne_mouvement (
    id             BIGSERIAL PRIMARY KEY,
    mouvement_id   BIGINT NOT NULL REFERENCES mouvement (id) ON DELETE CASCADE,
    bien_id        BIGINT REFERENCES bien (id),
    compte_id      BIGINT NOT NULL REFERENCES compte_nomenclature (id),
    designation    VARCHAR(255) NOT NULL,
    quantite       NUMERIC(18,3) NOT NULL CHECK (quantite > 0),
    nature_unite   VARCHAR(50) NOT NULL,
    prix_unitaire  NUMERIC(18,2) NOT NULL CHECK (prix_unitaire >= 0),
    montant        NUMERIC(18,2) NOT NULL CHECK (montant >= 0),
    etat           VARCHAR(20),
    observation    VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);
CREATE INDEX ix_lm_mouvement ON ligne_mouvement (mouvement_id);
CREATE INDEX ix_lm_bien ON ligne_mouvement (bien_id);

--changeset gcm:002-journal
CREATE TABLE ecriture_journal (
    id                  BIGSERIAL PRIMARY KEY,
    structure_id        BIGINT NOT NULL REFERENCES structure (id),
    exercice_id         BIGINT NOT NULL REFERENCES exercice (id),
    numero_ordre        BIGINT NOT NULL CHECK (numero_ordre > 0),
    date_ecriture       DATE NOT NULL,
    mouvement_id        BIGINT NOT NULL REFERENCES mouvement (id),
    ligne_mouvement_id  BIGINT NOT NULL REFERENCES ligne_mouvement (id),
    compte_id           BIGINT NOT NULL REFERENCES compte_nomenclature (id),
    bien_id             BIGINT REFERENCES bien (id),
    libelle             VARCHAR(255) NOT NULL,
    reference_piece     VARCHAR(150),
    quantite_entree     NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (quantite_entree >= 0),
    quantite_sortie     NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (quantite_sortie >= 0),
    montant_entree      NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (montant_entree >= 0),
    montant_sortie      NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (montant_sortie >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    CONSTRAINT ux_journal_numero UNIQUE (structure_id, exercice_id, numero_ordre)
);
CREATE INDEX ix_journal_bien ON ecriture_journal (bien_id);
CREATE INDEX ix_journal_compte ON ecriture_journal (compte_id);

CREATE TABLE solde_ouverture (
    id           BIGSERIAL PRIMARY KEY,
    exercice_id  BIGINT NOT NULL REFERENCES exercice (id),
    bien_id      BIGINT NOT NULL REFERENCES bien (id),
    quantite     NUMERIC(18,3) NOT NULL CHECK (quantite >= 0),
    montant      NUMERIC(18,2) NOT NULL CHECK (montant >= 0),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_solde_ouverture UNIQUE (exercice_id, bien_id)
);
