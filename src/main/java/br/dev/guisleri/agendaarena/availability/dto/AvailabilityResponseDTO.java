package br.dev.guisleri.agendaarena.availability.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityResponseDTO(
        Long scheduleSlotId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        boolean available
) {
}
