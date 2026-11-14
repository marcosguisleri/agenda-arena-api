package br.dev.guisleri.agendaarena.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Único ponto da regra "o horário já começou?". Um horário que começa exatamente agora já conta
 * como iniciado. Usa o {@link Clock} da aplicação (fuso {@code America/Sao_Paulo}).
 */
@Component
public class StartTimeChecker {

    private final Clock clock;

    public StartTimeChecker(Clock clock) {
        this.clock = clock;
    }

    public boolean hasStarted(LocalDate date, LocalTime startTime) {
        return !LocalDateTime.of(date, startTime).isAfter(LocalDateTime.now(clock));
    }
}
