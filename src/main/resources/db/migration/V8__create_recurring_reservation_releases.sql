CREATE TABLE recurring_reservations_releases
(
    id                       BIGSERIAL PRIMARY KEY,
    recurring_reservation_id BIGINT                   NOT NULL,
    occurrence_date          DATE                     NOT NULL,
    released_at              TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_recurring_reservations_releases_reservation_date
        UNIQUE (recurring_reservation_id, occurrence_date),

    CONSTRAINT fk_recurring_reservations_releases_recurring_reservations
        FOREIGN KEY (recurring_reservation_id)
            REFERENCES recurring_reservations (id)
            ON DELETE RESTRICT

);