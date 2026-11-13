package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;

import java.util.List;

/**
 * Partida com os participantes em ordem de inscrição, já carregados para o DTO.
 */
public record MatchDetails(Match match, List<MatchParticipant> participants) {
}
