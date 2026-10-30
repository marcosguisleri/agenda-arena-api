package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateSlotBlockRequestDTO(

        @NotNull
        LocalDate blockDate,

        @Size(
                max = 255,
                message = "Reason must have at most 255 characters"
        )
        String reason
) {
}
