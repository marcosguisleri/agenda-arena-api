package br.dev.guisleri.agendaarena.schedule.exception;

public class ScheduleSlotNotBookableException extends RuntimeException {
    public ScheduleSlotNotBookableException(String message) {
        super(message);
    }
}
