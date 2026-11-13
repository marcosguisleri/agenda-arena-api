package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

public record TeamMemberResponseDTO(
        String name,
        PlayerPosition position
) {
    public static TeamMemberResponseDTO fromEntity(MatchParticipant participant) {
        return new TeamMemberResponseDTO(participant.getName(), participant.getPosition());
    }
}
