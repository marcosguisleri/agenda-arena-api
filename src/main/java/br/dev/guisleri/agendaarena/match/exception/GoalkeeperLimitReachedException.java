package br.dev.guisleri.agendaarena.match.exception;

public class GoalkeeperLimitReachedException extends RuntimeException {
    public GoalkeeperLimitReachedException(String message) {
        super(message);
    }
}
