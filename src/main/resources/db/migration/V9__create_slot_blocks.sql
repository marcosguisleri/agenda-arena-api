CREATE TABLE slot_blocks
(
    id               BIGSERIAL PRIMARY KEY,
    schedule_slot_id BIGINT                   NOT NULL,
    block_date       DATE                     NOT NULL,
    reason           VARCHAR(255)             NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_slot_blocks_schedule_slot
        FOREIGN KEY (schedule_slot_id)
            REFERENCES schedule_slots (id),

    CONSTRAINT uk_slot_blocks_slot_date
        UNIQUE (schedule_slot_id, block_date)
);
