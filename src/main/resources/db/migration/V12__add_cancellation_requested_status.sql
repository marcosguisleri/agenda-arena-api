-- Novo estado de reserva: CANCELLATION_REQUESTED (o organizador pediu o cancelamento e o
-- estabelecimento ainda não decidiu). A reserva continua ocupando o horário, então o índice único
-- parcial passa a cobri-la. O nome do índice é mantido porque o código mapeia a violação dele
-- para ReservationAlreadyExistsException.

ALTER TABLE reservations
    DROP CONSTRAINT chk_reservations_status_valid;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_status_valid
        CHECK (status IN ('CONFIRMED', 'CANCELLATION_REQUESTED', 'COMPLETED', 'NO_SHOW', 'CANCELLED'));

DROP INDEX idx_unique_active_reservation;

CREATE UNIQUE INDEX idx_unique_active_reservation
    ON reservations (schedule_slot_id, reservation_date)
    WHERE status IN ('CONFIRMED', 'CANCELLATION_REQUESTED', 'COMPLETED', 'NO_SHOW');
