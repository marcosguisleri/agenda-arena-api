package br.dev.guisleri.agendaarena.schedule.exception;

public class ScheduleSlotNotAllowedException extends RuntimeException {
    public ScheduleSlotNotAllowedException(String message) {
        super(message);
    }
}
