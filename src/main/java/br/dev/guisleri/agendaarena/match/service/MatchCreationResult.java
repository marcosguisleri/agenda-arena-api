package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;

/**
 * @param created {@code false} quando a partida já existia (criação idempotente)
 */
public record MatchCreationResult(Match match, boolean created) {
}
