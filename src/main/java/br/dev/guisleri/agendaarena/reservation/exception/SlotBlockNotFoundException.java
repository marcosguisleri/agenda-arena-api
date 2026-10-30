package br.dev.guisleri.agendaarena.reservation.exception;

public class SlotBlockNotFoundException extends RuntimeException {
    public SlotBlockNotFoundException(String message) {
        super(message);
    }
}
