-- Papéis de acesso e vínculo entre administradores e estabelecimentos.
-- Usuários existentes viram ORGANIZER (quem faz reservas).

ALTER TABLE users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'ORGANIZER';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role_valid
        CHECK (role IN ('ORGANIZER', 'ESTABLISHMENT_ADMIN', 'PLATFORM_ADMIN'));

CREATE TABLE establishment_admins
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    establishment_id BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_establishment_admins_user_establishment
        UNIQUE (user_id, establishment_id),
    CONSTRAINT fk_establishment_admins_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),
    CONSTRAINT fk_establishment_admins_establishment
        FOREIGN KEY (establishment_id)
            REFERENCES establishments (id)
);

CREATE INDEX idx_establishment_admins_establishment
    ON establishment_admins (establishment_id);
