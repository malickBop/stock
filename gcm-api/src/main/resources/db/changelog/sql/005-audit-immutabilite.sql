--liquibase formatted sql

--changeset gcm:005-fk-documents
ALTER TABLE document_reglementaire ADD CONSTRAINT fk_doc_recensement FOREIGN KEY (recensement_id) REFERENCES recensement (id);
ALTER TABLE document_reglementaire ADD CONSTRAINT fk_doc_reforme FOREIGN KEY (reforme_id) REFERENCES reforme (id);
ALTER TABLE document_reglementaire ADD CONSTRAINT fk_doc_certificat FOREIGN KEY (certificat_id) REFERENCES certificat_administratif (id);

--changeset gcm:005-reddition
CREATE TABLE reddition (
    id              BIGSERIAL PRIMARY KEY,
    structure_id    BIGINT NOT NULL REFERENCES structure (id),
    exercice_id     BIGINT NOT NULL REFERENCES exercice (id),
    type_reddition  VARCHAR(30) NOT NULL CHECK (type_reddition IN ('COMPTE_GESTION','COMPTE_CENTRAL','COMPTE_ADMINISTRATIF','BORDEREAU_CENTRALISATION')),
    statut          VARCHAR(20) NOT NULL DEFAULT 'BROUILLON' CHECK (statut IN ('BROUILLON','EN_COURS','VALIDE','CLOTURE','ANNULE')),
    date_depot      DATE,
    document_id     BIGINT REFERENCES document_reglementaire (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_reddition UNIQUE (structure_id, exercice_id, type_reddition)
);

--changeset gcm:005-audit
CREATE TABLE audit_log (
    id                 BIGSERIAL PRIMARY KEY,
    date_action        TIMESTAMPTZ NOT NULL DEFAULT now(),
    utilisateur_login  VARCHAR(100),
    action             VARCHAR(60) NOT NULL,
    entite             VARCHAR(60) NOT NULL,
    entite_id          BIGINT,
    structure_code     VARCHAR(20),
    details            TEXT,
    adresse_ip         VARCHAR(60)
);
CREATE INDEX ix_audit_entite ON audit_log (entite, entite_id);
CREATE INDEX ix_audit_date ON audit_log (date_action);

--changeset gcm:005-immutabilite splitStatements:false
-- Le livre-journal, l'historique des documents et le journal d'audit sont en ajout seul (append-only).
CREATE OR REPLACE FUNCTION gcm_interdire_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % en ajout seul : modification/suppression interdite', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_immuable BEFORE UPDATE OR DELETE ON ecriture_journal
    FOR EACH ROW EXECUTE FUNCTION gcm_interdire_modification();
CREATE TRIGGER trg_audit_immuable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION gcm_interdire_modification();
CREATE TRIGGER trg_historique_immuable BEFORE UPDATE OR DELETE ON document_historique
    FOR EACH ROW EXECUTE FUNCTION gcm_interdire_modification();

-- Un document validé/archivé ne peut plus être modifié sauf passage à ARCHIVE.
CREATE OR REPLACE FUNCTION gcm_proteger_document_valide() RETURNS trigger AS $$
BEGIN
    IF OLD.statut = 'ARCHIVE' THEN
        RAISE EXCEPTION 'Document % archivé : non modifiable', OLD.numero USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' AND OLD.statut <> 'BROUILLON' THEN
        RAISE EXCEPTION 'Document % engagé dans le circuit : suppression interdite', OLD.numero USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_protege BEFORE UPDATE OR DELETE ON document_reglementaire
    FOR EACH ROW EXECUTE FUNCTION gcm_proteger_document_valide();

-- Un mouvement validé est définitif.
CREATE OR REPLACE FUNCTION gcm_proteger_mouvement_valide() RETURNS trigger AS $$
BEGIN
    IF OLD.statut = 'VALIDE' AND (TG_OP = 'DELETE' OR NEW.statut <> 'VALIDE') THEN
        RAISE EXCEPTION 'Mouvement % validé : non modifiable', OLD.numero USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mouvement_protege BEFORE UPDATE OR DELETE ON mouvement
    FOR EACH ROW EXECUTE FUNCTION gcm_proteger_mouvement_valide();
