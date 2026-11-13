package br.dev.guisleri.agendaarena.match.exception;

public class MatchParticipantNotFoundException extends RuntimeException {
    public MatchParticipantNotFoundException(String message) {
        super(message);
    }
}
