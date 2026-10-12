ALTER TABLE sports_fields
    ADD COLUMN slot_duration_minutes INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_sports_fields_slot_duration_positive
        CHECK (slot_duration_minutes > 0);

CREATE TABLE schedule_slots
(
    id             BIGSERIAL PRIMARY KEY,
    sport_field_id BIGINT         NOT NULL,
    day_of_week    VARCHAR(9)     NOT NULL,
    start_time     TIME           NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_schedule_slots_sport_field
        FOREIGN KEY (sport_field_id)
            REFERENCES sports_fields (id),

    CONSTRAINT uk_schedule_slots_field_day_time
        UNIQUE (sport_field_id, day_of_week, start_time),

    CONSTRAINT ck_schedule_slots_price_non_negative
        CHECK (price >= 0),

    CONSTRAINT ck_schedule_slots_day_of_week
        CHECK (day_of_week IN (
                               'MONDAY',
                               'TUESDAY',
                               'WEDNESDAY',
                               'THURSDAY',
                               'FRIDAY',
                               'SATURDAY',
                               'SUNDAY'
            ))
);