package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.MatchTeam;

/**
 * @param team A, B ou nulo (sem time)
 */
public record ChangeParticipantTeamRequestDTO(
        MatchTeam team
) {
}
