package br.dev.guisleri.agendaarena.reservation.exception;

public class RecurringReservationConflictException extends RuntimeException {
    public RecurringReservationConflictException(String message) {
        super(message);
    }
}
