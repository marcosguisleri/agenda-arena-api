package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Partida com os participantes em ordem de inscrição, já carregados para o DTO.
 *
 * @param shares cota de cada participante pelo id (zero para isentos): congelada se a partida
 *               está fechada, calculada na hora se está aberta
 */
public record MatchDetails(
        Match match,
        List<MatchParticipant> participants,
        Map<Long, BigDecimal> shares
) {
    public BigDecimal shareOf(MatchParticipant participant) {
        return shares.get(participant.getId());
    }
}
