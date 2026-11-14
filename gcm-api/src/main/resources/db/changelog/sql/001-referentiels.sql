--liquibase formatted sql

--changeset gcm:001-structure
CREATE TABLE structure (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(20)  NOT NULL UNIQUE,
    libelle           VARCHAR(255) NOT NULL,
    sigle             VARCHAR(50),
    type_structure    VARCHAR(20)  NOT NULL CHECK (type_structure IN ('PRINCIPALE','SECONDAIRE')),
    type_service      VARCHAR(20)  NOT NULL CHECK (type_service IN ('CENTRAL','DECONCENTRE')),
    parent_id         BIGINT REFERENCES structure (id),
    region            VARCHAR(100),
    departement       VARCHAR(100),
    adresse           VARCHAR(255),
    code_ordonnateur  VARCHAR(50),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_structure_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_structure_parent ON structure (parent_id);
-- Une seule structure principale.
CREATE UNIQUE INDEX ux_structure_principale ON structure (type_structure) WHERE type_structure = 'PRINCIPALE';

--changeset gcm:001-acteur
CREATE TABLE acteur (
    id          BIGSERIAL PRIMARY KEY,
    matricule   VARCHAR(50) UNIQUE,
    nom         VARCHAR(100) NOT NULL,
    prenom      VARCHAR(150),
    titre       VARCHAR(150),
    email       VARCHAR(150),
    telephone   VARCHAR(50),
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

CREATE TABLE acteur_fonction (
    id                  BIGSERIAL PRIMARY KEY,
    acteur_id           BIGINT NOT NULL REFERENCES acteur (id),
    structure_id        BIGINT NOT NULL REFERENCES structure (id),
    fonction            VARCHAR(40) NOT NULL CHECK (fonction IN ('ORDONNATEUR_PRINCIPAL','ORDONNATEUR_DELEGUE','ORDONNATEUR_SECONDAIRE',
                         'COMPTABLE_ORDRE','COMPTABLE_PRINCIPAL','COMPTABLE_SECONDAIRE','MAGASINIER','DETENTEUR','UTILISATEUR_FINAL',
                         'CONTROLEUR_BUDGETAIRE','INSPECTEUR_VERIFICATEUR')),
    reference_acte      VARCHAR(150),
    date_nomination     DATE,
    date_accreditation  DATE,
    date_fin            DATE,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_af_dates CHECK (date_fin IS NULL OR date_nomination IS NULL OR date_fin >= date_nomination)
);
CREATE INDEX ix_af_acteur ON acteur_fonction (acteur_id);
CREATE INDEX ix_af_structure ON acteur_fonction (structure_id);
-- Un seul comptable des matières actif par structure (art. 30).
CREATE UNIQUE INDEX ux_af_comptable_structure ON acteur_fonction (structure_id)
    WHERE actif AND fonction IN ('COMPTABLE_PRINCIPAL','COMPTABLE_SECONDAIRE');
-- Un seul ordonnateur principal actif.
CREATE UNIQUE INDEX ux_af_ordonnateur_principal ON acteur_fonction (fonction)
    WHERE actif AND fonction = 'ORDONNATEUR_PRINCIPAL';

CREATE TABLE lien_parente (
    id                BIGSERIAL PRIMARY KEY,
    acteur_id         BIGINT NOT NULL REFERENCES acteur (id),
    parent_acteur_id  BIGINT NOT NULL REFERENCES acteur (id),
    nature_lien       VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_lien_distinct CHECK (acteur_id <> parent_acteur_id),
    CONSTRAINT ux_lien UNIQUE (acteur_id, parent_acteur_id)
);

--changeset gcm:001-exercice
CREATE TABLE exercice (
    id            BIGSERIAL PRIMARY KEY,
    annee         INTEGER NOT NULL UNIQUE CHECK (annee BETWEEN 2000 AND 2100),
    statut        VARCHAR(20) NOT NULL DEFAULT 'OUVERT' CHECK (statut IN ('OUVERT','CLOTURE')),
    date_cloture  DATE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

CREATE TABLE commission (
    id                  BIGSERIAL PRIMARY KEY,
    structure_id        BIGINT NOT NULL REFERENCES structure (id),
    exercice_id         BIGINT NOT NULL REFERENCES exercice (id),
    reference_decision  VARCHAR(150),
    date_decision       DATE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_commission UNIQUE (structure_id, exercice_id)
);

CREATE TABLE commission_membre (
    id               BIGSERIAL PRIMARY KEY,
    commission_id    BIGINT NOT NULL REFERENCES commission (id) ON DELETE CASCADE,
    acteur_id        BIGINT NOT NULL REFERENCES acteur (id),
    role_commission  VARCHAR(20) NOT NULL CHECK (role_commission IN ('PRESIDENT','SUPPLEANT','MEMBRE')),
    rang             INTEGER NOT NULL CHECK (rang BETWEEN 1 AND 4),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_cm_rang UNIQUE (commission_id, rang),
    CONSTRAINT ux_cm_acteur UNIQUE (commission_id, acteur_id)
);
CREATE UNIQUE INDEX ux_cm_president ON commission_membre (commission_id) WHERE role_commission = 'PRESIDENT';
CREATE UNIQUE INDEX ux_cm_suppleant ON commission_membre (commission_id) WHERE role_commission = 'SUPPLEANT';

--changeset gcm:001-nomenclature
CREATE TABLE compte_nomenclature (
    id                    BIGSERIAL PRIMARY KEY,
    numero_complet        VARCHAR(30)  NOT NULL UNIQUE,
    groupe                INTEGER      NOT NULL CHECK (groupe IN (1, 2)),
    compte_principal      VARCHAR(4)   NOT NULL,
    compte_secondaire     VARCHAR(6)   NOT NULL,
    compte_divisionnaire  VARCHAR(8)   NOT NULL,
    sous_compte           VARCHAR(8)   NOT NULL,
    libelle               VARCHAR(255) NOT NULL,
    nature_unite          VARCHAR(50),
    actif                 BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

CREATE TABLE seuil_reglementaire (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    libelle          VARCHAR(255) NOT NULL,
    type_service     VARCHAR(20) CHECK (type_service IN ('CENTRAL','DECONCENTRE')),
    montant          NUMERIC(18,2) NOT NULL CHECK (montant >= 0),
    reference_texte  VARCHAR(150),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

--changeset gcm:001-securite
CREATE TABLE utilisateur (
    id                  BIGSERIAL PRIMARY KEY,
    login               VARCHAR(100) NOT NULL UNIQUE,
    mot_de_passe        VARCHAR(100) NOT NULL,
    nom_complet         VARCHAR(200) NOT NULL,
    email               VARCHAR(150),
    structure_id        BIGINT REFERENCES structure (id),
    acteur_id           BIGINT REFERENCES acteur (id),
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    derniere_connexion  TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);

CREATE TABLE utilisateur_role (
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    role            VARCHAR(40) NOT NULL,
    PRIMARY KEY (utilisateur_id, role)
);

CREATE TABLE api_client (
    id           BIGSERIAL PRIMARY KEY,
    nom          VARCHAR(100) NOT NULL UNIQUE,
    cle_hash     VARCHAR(100) NOT NULL UNIQUE,
    description  VARCHAR(255),
    actif        BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100)
);
