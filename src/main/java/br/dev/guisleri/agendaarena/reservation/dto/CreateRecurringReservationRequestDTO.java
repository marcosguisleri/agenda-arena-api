package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateRecurringReservationRequestDTO(

        @Positive
        Long organizerId,

        @NotBlank
        @Size(
                max = 120,
                message = "Name must have at most 120 characters"
        )
        String responsibleName,

        @NotBlank
        @Size(
                max = 20,
                message = "Phone must have at most 20 characters"
        )
        @Pattern(
                regexp = "^(?=(?:\\D*\\d){10,11}\\D*$)[0-9().\\s-]+$",
                message = "Phone must contain 10 or 11 digits"
        )
        String responsiblePhone,

        @NotNull
        LocalDate startDate,

        LocalDate endDate
) {
}
