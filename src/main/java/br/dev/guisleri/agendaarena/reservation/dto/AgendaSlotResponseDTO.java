package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.model.AgendaSlotStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Uma linha da agenda do administrador: um horário do campo em uma data.
 * {@code reservation}, {@code block} e {@code recurring} só vêm preenchidos quando existem
 * naquela data. Uma recorrência com {@code released = true} não ocupa o horário na data.
 */
public record AgendaSlotResponseDTO(
        Long scheduleSlotId,
        boolean active,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        AgendaSlotStatus status,
        ReservationResponseDTO reservation,
        SlotBlockResponseDTO block,
        AgendaRecurringReservationDTO recurring
) {

    public record AgendaRecurringReservationDTO(
            Long id,
            Long organizerId,
            String responsibleName,
            String responsiblePhone,
            LocalDate startDate,
            LocalDate endDate,
            boolean released
    ) {
    }
}
