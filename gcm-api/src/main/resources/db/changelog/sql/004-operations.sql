--liquibase formatted sql

--changeset gcm:004-recensement
CREATE TABLE recensement (
    id            BIGSERIAL PRIMARY KEY,
    numero        VARCHAR(40) NOT NULL UNIQUE,
    structure_id  BIGINT NOT NULL REFERENCES structure (id),
    exercice_id   BIGINT NOT NULL REFERENCES exercice (id),
    date_debut    DATE NOT NULL,
    date_fin      DATE,
    statut        VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','EN_COURS','VALIDE','CLOTURE','ANNULE')),
    observations  TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_recensement_dates CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

CREATE TABLE recensement_ligne (
    id              BIGSERIAL PRIMARY KEY,
    recensement_id  BIGINT NOT NULL REFERENCES recensement (id) ON DELETE CASCADE,
    bien_id         BIGINT NOT NULL REFERENCES bien (id),
    qte_theorique   NUMERIC(18,3) NOT NULL CHECK (qte_theorique >= 0),
    qte_physique    NUMERIC(18,3) CHECK (qte_physique >= 0),
    prix_unitaire   NUMERIC(18,2) NOT NULL CHECK (prix_unitaire >= 0),
    etat            VARCHAR(20),
    observation     VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_recensement_ligne UNIQUE (recensement_id, bien_id)
);

--changeset gcm:004-reforme
CREATE TABLE reforme (
    id                   BIGSERIAL PRIMARY KEY,
    numero               VARCHAR(40) NOT NULL UNIQUE,
    structure_id         BIGINT NOT NULL REFERENCES structure (id),
    exercice_id          BIGINT NOT NULL REFERENCES exercice (id),
    date_proposition     DATE NOT NULL,
    motif                VARCHAR(500) NOT NULL,
    statut               VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','EN_COURS','VALIDE','CLOTURE','ANNULE')),
    decision             VARCHAR(20) CHECK (decision IN ('VENTE','DESTRUCTION','CESSION')),
    date_execution       DATE,
    produit_vente        NUMERIC(18,2) CHECK (produit_vente >= 0),
    reference_quittance  VARCHAR(150),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

CREATE TABLE reforme_ligne (
    id               BIGSERIAL PRIMARY KEY,
    reforme_id       BIGINT NOT NULL REFERENCES reforme (id) ON DELETE CASCADE,
    bien_id          BIGINT NOT NULL REFERENCES bien (id),
    quantite         NUMERIC(18,3) NOT NULL CHECK (quantite > 0),
    valeur_unitaire  NUMERIC(18,2) NOT NULL CHECK (valeur_unitaire >= 0),
    etat             VARCHAR(20),
    motif            VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_reforme_ligne UNIQUE (reforme_id, bien_id)
);

--changeset gcm:004-certificat
CREATE TABLE certificat_administratif (
    id               BIGSERIAL PRIMARY KEY,
    numero           VARCHAR(40) NOT NULL UNIQUE,
    structure_id     BIGINT NOT NULL REFERENCES structure (id),
    exercice_id      BIGINT NOT NULL REFERENCES exercice (id),
    objet            VARCHAR(20) NOT NULL CHECK (objet IN ('PERTE','VOL','DESTRUCTION','CONSOMMATION','REGULARISATION')),
    motif            VARCHAR(1000) NOT NULL,
    date_certificat  DATE NOT NULL,
    statut           VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','EN_COURS','VALIDE','CLOTURE','ANNULE')),
    mouvement_id     BIGINT REFERENCES mouvement (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

--changeset gcm:004-immeuble
CREATE TABLE immeuble (
    id                BIGSERIAL PRIMARY KEY,
    reference         VARCHAR(40) NOT NULL UNIQUE,
    structure_id      BIGINT NOT NULL REFERENCES structure (id),
    type_immeuble     VARCHAR(20) NOT NULL CHECK (type_immeuble IN ('BATI','NON_BATI','BAIL')),
    compte_id         BIGINT REFERENCES compte_nomenclature (id),
    designation       VARCHAR(255) NOT NULL,
    localisation      VARCHAR(255) NOT NULL,
    titre_foncier     VARCHAR(100),
    superficie        NUMERIC(18,2) CHECK (superficie >= 0),
    superficie_batie  NUMERIC(18,2) CHECK (superficie_batie >= 0),
    nombre_pieces     INTEGER CHECK (nombre_pieces >= 0),
    valeur            NUMERIC(18,2) CHECK (valeur >= 0),
    date_acquisition  DATE,
    mode_acquisition  VARCHAR(100),
    affectation       VARCHAR(255),
    etat              VARCHAR(20),
    bailleur          VARCHAR(200),
    loyer_mensuel     NUMERIC(18,2) CHECK (loyer_mensuel >= 0),
    date_debut_bail   DATE,
    date_fin_bail     DATE,
    observations      VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_immeuble_bail CHECK (type_immeuble <> 'BAIL' OR bailleur IS NOT NULL),
    CONSTRAINT ck_immeuble_bail_dates CHECK (date_fin_bail IS NULL OR date_debut_bail IS NULL OR date_fin_bail >= date_debut_bail)
);
