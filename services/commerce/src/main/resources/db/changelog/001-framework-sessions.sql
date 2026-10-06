-- Spring Session JDBC 4.1.1 PostgreSQL schema, owner-qualified/lowercase.
CREATE TABLE commerce.spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_primary_id_pk PRIMARY KEY (primary_id),
    CONSTRAINT spring_session_interval_ck CHECK (max_inactive_interval > 0)
);
CREATE UNIQUE INDEX spring_session_session_id_uq ON commerce.spring_session (session_id);
CREATE INDEX spring_session_expiry_time_ix ON commerce.spring_session (expiry_time);
CREATE INDEX spring_session_principal_name_ix ON commerce.spring_session (principal_name);
CREATE TABLE commerce.spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_session_fk FOREIGN KEY (session_primary_id)
        REFERENCES commerce.spring_session(primary_id) ON DELETE CASCADE
);
GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.spring_session, commerce.spring_session_attributes TO nigson_commerce_app;
