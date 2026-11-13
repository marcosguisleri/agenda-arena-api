package br.dev.guisleri.agendaarena.match.dto;

import jakarta.validation.constraints.Size;

/**
 * @param pixKey chave exibida na página; nula ou vazia remove
 */
public record ChangePixKeyRequestDTO(

        @Size(max = 140)
        String pixKey

) {
}
