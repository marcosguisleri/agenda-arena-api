package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import jakarta.validation.constraints.NotNull;

public record ChangeParticipantPositionRequestDTO(

        @NotNull
        PlayerPosition position

) {
}
