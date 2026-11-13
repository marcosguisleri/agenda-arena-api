package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.ShirtColor;

import java.util.Arrays;
import java.util.List;

/**
 * @param shirtColor nula enquanto o organizador não escolheu
 */
public record TeamResponseDTO(
        MatchTeam team,
        String name,
        ShirtColor shirtColor
) {
    public static List<TeamResponseDTO> fromMatch(Match match) {
        return Arrays.stream(MatchTeam.values())
                .map(team -> new TeamResponseDTO(
                        team,
                        match.getTeamName(team),
                        match.getTeamShirtColor(team)
                ))
                .toList();
    }
}
