package br.dev.guisleri.agendaarena.match.exception;

public class FieldPlayerLimitReachedException extends RuntimeException {
    public FieldPlayerLimitReachedException(String message) {
        super(message);
    }
}
