package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotNull;

public record LinkRecurringReservationOrganizerRequestDTO(

        @NotNull
        Long organizerId
) {
}
