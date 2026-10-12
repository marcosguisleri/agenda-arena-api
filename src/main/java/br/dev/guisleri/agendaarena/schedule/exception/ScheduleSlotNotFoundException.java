package br.dev.guisleri.agendaarena.schedule.exception;

public class ScheduleSlotNotFoundException extends RuntimeException {
    public ScheduleSlotNotFoundException(String message) {
        super(message);
    }
}
