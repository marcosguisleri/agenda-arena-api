package br.dev.guisleri.agendaarena.match.service;

/**
 * @param created {@code false} quando a partida já existia (criação idempotente)
 */
public record MatchCreationResult(MatchDetails details, boolean created) {
}
