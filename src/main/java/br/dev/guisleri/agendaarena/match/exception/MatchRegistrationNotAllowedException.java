package br.dev.guisleri.agendaarena.match.exception;

public class MatchRegistrationNotAllowedException extends RuntimeException {
    public MatchRegistrationNotAllowedException(String message) {
        super(message);
    }
}
