package br.dev.guisleri.agendaarena.sportfield.exception;

public class SportFieldAlreadyExistsException extends RuntimeException {
    public SportFieldAlreadyExistsException(String message) {
        super(message);
    }
}
