CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE recurring_reservations
(
    id                BIGSERIAL PRIMARY KEY,
    schedule_slot_id  BIGINT       NOT NULL,
    organizer_id      BIGINT       NULL,
    responsible_name  VARCHAR(120) NOT NULL,
    responsible_phone VARCHAR(20)  NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NULL,

    CONSTRAINT chk_end_date_after_start
        CHECK (end_date IS NULL OR end_date >= start_date),

    CONSTRAINT fk_recurring_reservations_organizer
        FOREIGN KEY (organizer_id)
            REFERENCES users (id),

    CONSTRAINT fk_recurring_reservations_schedule_slot
        FOREIGN KEY (schedule_slot_id)
            REFERENCES schedule_slots (id),

    CONSTRAINT ex_recurring_reservations_no_overlap
        EXCLUDE USING gist (
            schedule_slot_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        )
);

CREATE INDEX idx_recurring_reservations_slot_dates
    ON recurring_reservations (schedule_slot_id, start_date, end_date);