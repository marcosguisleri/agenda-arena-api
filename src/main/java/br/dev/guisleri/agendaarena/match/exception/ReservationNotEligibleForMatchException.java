package br.dev.guisleri.agendaarena.match.exception;

public class ReservationNotEligibleForMatchException extends RuntimeException {
    public ReservationNotEligibleForMatchException(String message) {
        super(message);
    }
}
