ALTER TABLE reservations
    ADD COLUMN origin VARCHAR(20);

ALTER TABLE reservations
    ADD COLUMN customer_name VARCHAR(120);

ALTER TABLE reservations
    ADD COLUMN customer_phone VARCHAR(20);

UPDATE reservations
SET origin = 'ONLINE';

ALTER TABLE reservations
    ALTER COLUMN origin SET NOT NULL;

ALTER TABLE reservations
    ALTER COLUMN organizer_id DROP NOT NULL;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservation_origin_data
        CHECK (
            (
                origin = 'ONLINE'
                    AND organizer_id IS NOT NULL
                    AND customer_name IS NULL
                    AND customer_phone IS NULL
                )
                OR
            (
                origin = 'MANUAL'
                    AND organizer_id IS NULL
                    AND customer_name IS NOT NULL
                    AND btrim(customer_name) <> ''
                )
            );
