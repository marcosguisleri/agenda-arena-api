package br.dev.guisleri.agendaarena.schedule.exception;

public class ScheduleSlotAlreadyExistsException extends RuntimeException {
    public ScheduleSlotAlreadyExistsException(String message) {
        super(message);
    }
}
