package br.dev.guisleri.agendaarena.reservation.exception;

public class SlotBlockNotAllowedException extends RuntimeException {
    public SlotBlockNotAllowedException(String message) {
        super(message);
    }
}
