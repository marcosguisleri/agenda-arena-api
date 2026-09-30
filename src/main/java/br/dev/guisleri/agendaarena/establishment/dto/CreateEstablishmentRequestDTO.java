package br.dev.guisleri.agendaarena.establishment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEstablishmentRequestDTO(

        @NotBlank(message = "não deve estar em branco")
        @Size(
                max = 120,
                message = "deve ter no máximo 120 caracteres"
        )
        String name

) {
}
