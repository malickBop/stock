--liquibase formatted sql

--changeset gcm:003-document
CREATE TABLE document_reglementaire (
    id                      BIGSERIAL PRIMARY KEY,
    modele                  INTEGER NOT NULL CHECK (modele BETWEEN 1 AND 26),
    numero                  VARCHAR(60) NOT NULL UNIQUE,
    titre                   VARCHAR(255) NOT NULL,
    structure_id            BIGINT NOT NULL REFERENCES structure (id),
    exercice_id             BIGINT NOT NULL REFERENCES exercice (id),
    statut                  VARCHAR(30) NOT NULL DEFAULT 'BROUILLON'
                            CHECK (statut IN ('BROUILLON','SIGNE_COMMISSION','EN_ATTENTE_VISA_EXTERNE','SCAN_IMPORTE','VALIDATION_FINALE','ARCHIVE','REJETE')),
    mouvement_id            BIGINT REFERENCES mouvement (id),
    recensement_id          BIGINT,
    reforme_id              BIGINT,
    certificat_id           BIGINT,
    document_parent_id      BIGINT REFERENCES document_reglementaire (id),
    requiert_commission     BOOLEAN NOT NULL DEFAULT FALSE,
    montant_total           NUMERIC(18,2) CHECK (montant_total >= 0),
    numero_version          INTEGER NOT NULL DEFAULT 1 CHECK (numero_version >= 1),
    date_validation_finale  TIMESTAMPTZ,
    valide_par              VARCHAR(100),
    motif_rejet             VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_doc_validation CHECK (statut NOT IN ('VALIDATION_FINALE','ARCHIVE') OR date_validation_finale IS NOT NULL)
);
CREATE INDEX ix_doc_struct_ex ON document_reglementaire (structure_id, exercice_id);
CREATE INDEX ix_doc_statut ON document_reglementaire (statut);
CREATE INDEX ix_doc_mouvement ON document_reglementaire (mouvement_id);

CREATE TABLE document_signature (
    id                 BIGSERIAL PRIMARY KEY,
    document_id        BIGINT NOT NULL REFERENCES document_reglementaire (id) ON DELETE CASCADE,
    role_signature     VARCHAR(30) NOT NULL,
    acteur_id          BIGINT REFERENCES acteur (id),
    signataire_nom     VARCHAR(200) NOT NULL,
    date_signature     TIMESTAMPTZ NOT NULL,
    utilisateur_login  VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_doc_signature UNIQUE (document_id, role_signature, acteur_id)
);

CREATE TABLE piece_jointe (
    id                  BIGSERIAL PRIMARY KEY,
    document_id         BIGINT REFERENCES document_reglementaire (id),
    mouvement_id        BIGINT REFERENCES mouvement (id),
    type_piece          VARCHAR(30) NOT NULL CHECK (type_piece IN ('SCAN_SIGNE','FACTURE','BON_LIVRAISON','CONTRAT','DECISION','AUTRE')),
    nom_fichier         VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    taille              BIGINT NOT NULL CHECK (taille > 0),
    sha256              VARCHAR(64) NOT NULL,
    chemin_stockage     VARCHAR(500) NOT NULL,
    date_fin_retention  DATE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ck_pj_rattachement CHECK (document_id IS NOT NULL OR mouvement_id IS NOT NULL)
);
CREATE INDEX ix_pj_document ON piece_jointe (document_id);

CREATE TABLE document_visa_externe (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT NOT NULL REFERENCES document_reglementaire (id) ON DELETE CASCADE,
    type_visa        VARCHAR(30) NOT NULL CHECK (type_visa IN ('CBM','CRF','DGB','INSPECTEUR_VERIFICATEUR','RECEPTIONNAIRE')),
    autorite         VARCHAR(200),
    reference_visa   VARCHAR(150),
    date_visa        DATE NOT NULL,
    piece_jointe_id  BIGINT REFERENCES piece_jointe (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_doc_visa UNIQUE (document_id, type_visa)
);

CREATE TABLE document_historique (
    id                 BIGSERIAL PRIMARY KEY,
    document_id        BIGINT NOT NULL REFERENCES document_reglementaire (id) ON DELETE CASCADE,
    ancien_statut      VARCHAR(30),
    nouveau_statut     VARCHAR(30) NOT NULL,
    action             VARCHAR(50) NOT NULL,
    utilisateur_login  VARCHAR(100),
    commentaire        VARCHAR(1000),
    date_action        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_dh_document ON document_historique (document_id);
