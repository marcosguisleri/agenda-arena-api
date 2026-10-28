package br.dev.guisleri.agendaarena.reservation.exception;

public class RecurringReservationNotAllowedException extends RuntimeException {
    public RecurringReservationNotAllowedException(String message) {
        super(message);
    }
}
