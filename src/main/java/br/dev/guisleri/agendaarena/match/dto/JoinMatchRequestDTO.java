package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinMatchRequestDTO(

        @NotBlank
        @Size(max = 60)
        String name,

        @NotNull
        PlayerPosition position

) {
}
