-- Partida, fatia 4: times A e B (montagem manual e sorteio), com nome e cor de camiseta.
-- O estado FINISHED foi retirado do escopo (decisão do dono, 13/11/2026): a partida fica só
-- OPEN ou CLOSED. A V14 não é editada; o CHECK de status é recriado aqui.

ALTER TABLE matches
    DROP CONSTRAINT chk_matches_status_valid;

ALTER TABLE matches
    ADD CONSTRAINT chk_matches_status_valid
        CHECK (status IN ('OPEN', 'CLOSED'));

ALTER TABLE matches
    ADD COLUMN team_a_name       VARCHAR(30) NOT NULL DEFAULT 'Time A',
    ADD COLUMN team_a_shirt_color VARCHAR(20),
    ADD COLUMN team_b_name       VARCHAR(30) NOT NULL DEFAULT 'Time B',
    ADD COLUMN team_b_shirt_color VARCHAR(20),
    ADD COLUMN teams_updated_at  TIMESTAMPTZ;

ALTER TABLE matches
    ADD CONSTRAINT chk_matches_shirt_colors_valid
        CHECK (
            (team_a_shirt_color IS NULL OR team_a_shirt_color IN
                ('WHITE', 'BLACK', 'RED', 'BLUE', 'GREEN', 'YELLOW', 'ORANGE', 'GRAY', 'PURPLE',
                 'PINK'))
            AND (team_b_shirt_color IS NULL OR team_b_shirt_color IN
                ('WHITE', 'BLACK', 'RED', 'BLUE', 'GREEN', 'YELLOW', 'ORANGE', 'GRAY', 'PURPLE',
                 'PINK'))
        ),
    ADD CONSTRAINT chk_matches_shirt_colors_distinct
        CHECK (team_a_shirt_color IS NULL OR team_b_shirt_color IS NULL
            OR team_a_shirt_color <> team_b_shirt_color);

-- Time do participante: A, B ou nulo (sem time).
ALTER TABLE match_participants
    ADD COLUMN team VARCHAR(1);

ALTER TABLE match_participants
    ADD CONSTRAINT chk_match_participants_team_valid
        CHECK (team IS NULL OR team IN ('A', 'B'));
