-- Novos estados de reserva: COMPLETED (concluída) e NO_SHOW (não compareceu).
-- Os dois continuam ocupando o horário (decisão de produto), então o índice único
-- parcial passa a cobrir CONFIRMED, COMPLETED e NO_SHOW. O nome do índice é mantido
-- porque o código mapeia a violação dele para ReservationAlreadyExistsException.

ALTER TABLE reservations
    DROP CONSTRAINT chk_reservations_status_valid;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_status_valid
        CHECK (status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW', 'CANCELLED'));

DROP INDEX idx_unique_active_reservation;

CREATE UNIQUE INDEX idx_unique_active_reservation
    ON reservations (schedule_slot_id, reservation_date)
    WHERE status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW');
