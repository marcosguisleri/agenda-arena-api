-- Partida: uma página por reserva (regra "uma reserva = um slot"), acessada por um link público.
-- O link usa public_token (aleatório, não adivinhável), nunca o id sequencial.
-- Os estados CLOSED e FINISHED já entram no CHECK; as transições chegam nas próximas fatias.

CREATE TABLE matches
(
    id             BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT      NOT NULL,
    public_token   VARCHAR(43) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_matches_reservation
        UNIQUE (reservation_id),
    CONSTRAINT uk_matches_public_token
        UNIQUE (public_token),
    CONSTRAINT chk_matches_status_valid
        CHECK (status IN ('OPEN', 'CLOSED', 'FINISHED')),
    CONSTRAINT fk_matches_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservations (id)
);
