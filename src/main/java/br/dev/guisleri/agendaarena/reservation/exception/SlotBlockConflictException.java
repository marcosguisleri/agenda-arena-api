package br.dev.guisleri.agendaarena.reservation.exception;

public class SlotBlockConflictException extends RuntimeException {
    public SlotBlockConflictException(String message) {
        super(message);
    }
}
