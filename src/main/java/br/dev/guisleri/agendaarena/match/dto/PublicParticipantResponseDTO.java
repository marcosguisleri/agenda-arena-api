package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import java.math.BigDecimal;

/**
 * Visão de quem tem o link: sem id interno (quem edita a lista é só o organizador).
 */
public record PublicParticipantResponseDTO(
        String name,
        PlayerPosition position,
        boolean paysShare,
        BigDecimal shareAmount,
        boolean paid
) {
    public static PublicParticipantResponseDTO fromEntity(
            MatchParticipant participant,
            BigDecimal shareAmount
    ) {
        return new PublicParticipantResponseDTO(
                participant.getName(),
                participant.getPosition(),
                participant.isPaysShare(),
                shareAmount,
                participant.isPaid()
        );
    }
}
