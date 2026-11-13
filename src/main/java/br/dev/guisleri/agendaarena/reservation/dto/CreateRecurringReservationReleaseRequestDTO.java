package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateRecurringReservationReleaseRequestDTO(

        @NotNull
        LocalDate occurrenceDate
) {
}
