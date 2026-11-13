package br.dev.guisleri.agendaarena.match.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ChangeTeamsRequestDTO(

        @NotNull
        @Valid
        ChangeTeamSettingsRequestDTO teamA,

        @NotNull
        @Valid
        ChangeTeamSettingsRequestDTO teamB

) {
}
