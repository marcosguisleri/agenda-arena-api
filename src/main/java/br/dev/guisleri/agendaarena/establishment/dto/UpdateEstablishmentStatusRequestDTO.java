package br.dev.guisleri.agendaarena.establishment.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEstablishmentStatusRequestDTO(
        @NotNull
        Boolean active
) {
}
