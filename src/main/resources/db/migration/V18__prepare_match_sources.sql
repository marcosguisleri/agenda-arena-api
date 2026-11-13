-- Partida com duas origens possíveis: reserva avulsa (reservation_id) ou ocorrência de recorrência
-- (recurring_reservation_id + occurrence_date). Os dados que a partida usa (dono, horário, data,
-- preço) passam a ficar copiados nela, preenchidos na criação; as partidas existentes recebem os
-- valores da reserva de origem.

ALTER TABLE matches
    ADD COLUMN organizer_id             BIGINT,
    ADD COLUMN schedule_slot_id         BIGINT,
    ADD COLUMN match_date               DATE,
    ADD COLUMN start_time               TIME,
    ADD COLUMN end_time                 TIME,
    ADD COLUMN price                    NUMERIC(10, 2),
    ADD COLUMN recurring_reservation_id BIGINT,
    ADD COLUMN occurrence_date          DATE;

UPDATE matches m
SET organizer_id     = r.organizer_id,
    schedule_slot_id = r.schedule_slot_id,
    match_date       = r.reservation_date,
    start_time       = r.start_time,
    end_time         = r.end_time,
    price            = r.price
FROM reservations r
WHERE r.id = m.reservation_id;

ALTER TABLE matches
    ALTER COLUMN organizer_id SET NOT NULL,
    ALTER COLUMN schedule_slot_id SET NOT NULL,
    ALTER COLUMN match_date SET NOT NULL,
    ALTER COLUMN start_time SET NOT NULL,
    ALTER COLUMN end_time SET NOT NULL,
    ALTER COLUMN price SET NOT NULL,
    ALTER COLUMN reservation_id DROP NOT NULL;

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_organizer
        FOREIGN KEY (organizer_id)
            REFERENCES users (id),
    ADD CONSTRAINT fk_matches_schedule_slot
        FOREIGN KEY (schedule_slot_id)
            REFERENCES schedule_slots (id),
    ADD CONSTRAINT fk_matches_recurring_reservation
        FOREIGN KEY (recurring_reservation_id)
            REFERENCES recurring_reservations (id),
    ADD CONSTRAINT uk_matches_recurring_occurrence
        UNIQUE (recurring_reservation_id, occurrence_date),
    ADD CONSTRAINT chk_matches_single_source
        CHECK (
            (reservation_id IS NOT NULL
                AND recurring_reservation_id IS NULL
                AND occurrence_date IS NULL)
            OR (reservation_id IS NULL
                AND recurring_reservation_id IS NOT NULL
                AND occurrence_date IS NOT NULL)
        ),
    ADD CONSTRAINT chk_matches_occurrence_date_consistent
        CHECK (occurrence_date IS NULL OR occurrence_date = match_date),
    ADD CONSTRAINT chk_matches_price_non_negative
        CHECK (price >= 0),
    ADD CONSTRAINT chk_matches_time_order
        CHECK (end_time > start_time);

CREATE INDEX idx_matches_organizer
    ON matches (organizer_id);
