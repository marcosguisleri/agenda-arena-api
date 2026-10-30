package br.dev.guisleri.agendaarena.reservation.exception;

public class ReservationInvalidStateException extends RuntimeException {
    public ReservationInvalidStateException(String message) {
        super(message);
    }
}
