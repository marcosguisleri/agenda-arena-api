-- Participantes da partida (convidados sem conta, inscritos pelo link).
-- Nomes duplicados são permitidos: a identidade é o id. O limite de goleiros (4) é garantido
-- pelo lock pessimista da partida no serviço, não por constraint.

CREATE TABLE match_participants
(
    id         BIGSERIAL PRIMARY KEY,
    match_id   BIGINT      NOT NULL,
    name       VARCHAR(60) NOT NULL,
    position   VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_match_participants_position_valid
        CHECK (position IN ('FIELD', 'GOALKEEPER')),
    CONSTRAINT fk_match_participants_match
        FOREIGN KEY (match_id)
            REFERENCES matches (id)
);

CREATE INDEX idx_match_participants_match
    ON match_participants (match_id);
