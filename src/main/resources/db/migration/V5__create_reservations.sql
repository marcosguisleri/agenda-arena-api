CREATE TABLE reservations
(
    id               BIGSERIAL PRIMARY KEY,
    organizer_id     BIGINT         NOT NULL,
    schedule_slot_id BIGINT         NOT NULL,
    reservation_date DATE           NOT NULL,
    start_time       TIME           NOT NULL,
    end_time         TIME           NOT NULL,
    price            NUMERIC(10, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,

    CONSTRAINT chk_reservations_price_non_negative
        CHECK (price >= 0),
    CONSTRAINT chk_reservations_time_order
        CHECK (end_time > start_time),
    CONSTRAINT chk_reservations_status_valid
        CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT fk_reservations_organizer
        FOREIGN KEY (organizer_id)
            REFERENCES users (id),

    CONSTRAINT fk_reservations_schedule_slot
        FOREIGN KEY (schedule_slot_id)
            REFERENCES schedule_slots (id)
);

CREATE UNIQUE INDEX idx_unique_active_reservation
    ON reservations (schedule_slot_id, reservation_date)
    WHERE status = 'CONFIRMED';