package br.dev.guisleri.agendaarena.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateScheduleSlotRequestDTO (

        @NotNull
        DayOfWeek dayOfWeek,

        @NotNull
        LocalTime startTime,

        @NotNull
        @PositiveOrZero
        BigDecimal price

) {
}
