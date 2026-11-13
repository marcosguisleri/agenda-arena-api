package br.dev.guisleri.agendaarena.match.exception;

public class MatchInvalidStateException extends RuntimeException {
    public MatchInvalidStateException(String message) {
        super(message);
    }
}
