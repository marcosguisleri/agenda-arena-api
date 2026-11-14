-- Sessões de refresh token. Uma linha por login (não por renovação): o token atual e o anterior
-- ficam guardados só como hash SHA-256 (o token em si nunca vai para o banco). O anterior serve
-- para detectar reuso (possível roubo), que revoga a sessão inteira.

CREATE TABLE refresh_sessions
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    current_token_hash  VARCHAR(64) NOT NULL,
    previous_token_hash VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL,
    last_rotated_at     TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,

    CONSTRAINT uk_refresh_sessions_current_token_hash
        UNIQUE (current_token_hash),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_refresh_sessions_previous_token_hash
    ON refresh_sessions (previous_token_hash);

CREATE INDEX idx_refresh_sessions_user
    ON refresh_sessions (user_id);
