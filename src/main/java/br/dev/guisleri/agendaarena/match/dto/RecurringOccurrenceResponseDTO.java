package br.dev.guisleri.agendaarena.match.dto;

import java.time.LocalDate;

/**
 * Uma data futura de uma série recorrente. {@code released} indica ocorrência liberada
 * ("cancelada"); {@code matchId}/{@code matchPublicToken} só vêm preenchidos se já existe partida.
 */
public record RecurringOccurrenceResponseDTO(
        LocalDate date,
        boolean released,
        Long matchId,
        String matchPublicToken
) {
}
