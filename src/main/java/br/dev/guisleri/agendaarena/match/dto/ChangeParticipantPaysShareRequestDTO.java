package br.dev.guisleri.agendaarena.match.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeParticipantPaysShareRequestDTO(

        @NotNull
        Boolean paysShare

) {
}
