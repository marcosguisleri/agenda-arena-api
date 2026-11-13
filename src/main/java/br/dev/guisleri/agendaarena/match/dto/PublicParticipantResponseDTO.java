package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

/**
 * Visão de quem tem o link: sem id interno (quem edita a lista é só o organizador).
 */
public record PublicParticipantResponseDTO(
        String name,
        PlayerPosition position
) {
    public static PublicParticipantResponseDTO fromEntity(MatchParticipant participant) {
        return new PublicParticipantResponseDTO(
                participant.getName(),
                participant.getPosition()
        );
    }
}
