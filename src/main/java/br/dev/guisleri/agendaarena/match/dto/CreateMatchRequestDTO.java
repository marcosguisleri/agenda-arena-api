package br.dev.guisleri.agendaarena.match.dto;

import jakarta.validation.Valid;

/**
 * Corpo opcional da criação da partida. Sem corpo (ou sem {@code pix}), a partida nasce sem chave
 * Pix. Se a partida já existir, a criação é idempotente e o corpo é ignorado.
 */
public record CreateMatchRequestDTO(

        @Valid
        MatchPixRequestDTO pix

) {
}
