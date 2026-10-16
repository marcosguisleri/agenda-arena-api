package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateManualReservationRequestDTO(

        @NotNull
        LocalDate reservationDate,

        @NotBlank
        @Size(max = 120)
        String customerName,

        @Size(max = 20)
        String customerPhone

) {
}
