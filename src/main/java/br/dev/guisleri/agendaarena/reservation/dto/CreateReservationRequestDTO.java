package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateReservationRequestDTO(

        @NotNull
        @Positive
        Long organizerId,

        @NotNull
        LocalDate reservationDate

) {
}
