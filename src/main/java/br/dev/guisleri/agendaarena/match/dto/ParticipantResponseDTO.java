package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Visão do organizador: o id identifica o participante (nomes podem se repetir).
 *
 * @param amountChangedAfterPayment pagamento declarado com uma cota diferente da atual (a partida
 *                                  foi reaberta e a lista mudou)
 */
public record ParticipantResponseDTO(
        Long id,
        String name,
        PlayerPosition position,
        Instant joinedAt,
        boolean paysShare,
        BigDecimal shareAmount,
        boolean paid,
        BigDecimal paidAmount,
        boolean amountChangedAfterPayment
) {
    public static ParticipantResponseDTO fromEntity(
            MatchParticipant participant,
            BigDecimal shareAmount
    ) {
        return new ParticipantResponseDTO(
                participant.getId(),
                participant.getName(),
                participant.getPosition(),
                participant.getCreatedAt(),
                participant.isPaysShare(),
                shareAmount,
                participant.isPaid(),
                participant.getPaidAmount(),
                participant.isPaid()
                        && participant.getPaidAmount().compareTo(shareAmount) != 0
        );
    }
}
