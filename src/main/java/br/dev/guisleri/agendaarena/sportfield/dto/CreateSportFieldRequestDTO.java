package br.dev.guisleri.agendaarena.sportfield.dto;

import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSportFieldRequestDTO(

        @NotBlank(message = "não deve estar em branco")
        @Size(
                max = 120,
                message = "deve ter no máximo 120 caracteres"
        )
        String name,

        @NotNull
        SportType sportType
) {
}
