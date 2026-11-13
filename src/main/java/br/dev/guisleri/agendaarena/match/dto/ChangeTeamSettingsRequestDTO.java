package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.ShirtColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param shirtColor opcional; nula remove a cor
 */
public record ChangeTeamSettingsRequestDTO(

        @NotBlank
        @Size(max = 30)
        String name,

        ShirtColor shirtColor

) {
}
