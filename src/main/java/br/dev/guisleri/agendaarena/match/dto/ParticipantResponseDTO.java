package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import java.time.Instant;

/**
 * Visão do organizador: o id identifica o participante (nomes podem se repetir).
 */
public record ParticipantResponseDTO(
        Long id,
        String name,
        PlayerPosition position,
        Instant joinedAt
) {
    public static ParticipantResponseDTO fromEntity(MatchParticipant participant) {
        return new ParticipantResponseDTO(
                participant.getId(),
                participant.getName(),
                participant.getPosition(),
                participant.getCreatedAt()
        );
    }
}
