package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateManualReservationRequestDTO(

        @NotNull
        LocalDate reservationDate,

        @NotBlank
        @Size(max = 120)
        String customerName,

        @Size(
                max = 20,
                message = "Phone must have at most 20 characters"
        )
        @Pattern(
                regexp = "^$|^(?=(?:\\D*\\d){10,11}\\D*$)[0-9().\\s-]+$",
                message = "Phone must contain 10 or 11 digits"
        )
        String customerPhone

) {
}
