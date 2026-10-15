package br.dev.guisleri.agendaarena.availability.exception;

public class AvailabilityNotAllowedException extends RuntimeException {
    public AvailabilityNotAllowedException(String message) {
        super(message);
    }
}
