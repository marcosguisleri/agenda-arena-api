package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Visão do organizador: o id identifica o participante (nomes podem se repetir).
 *
 * @param amountChangedAfterPayment partida fechada e pagamento declarado com valor diferente da cota
 *                                  congelada (a partida foi reaberta depois do pagamento e a lista
 *                                  mudou). Com a partida aberta é sempre {@code false}: a cota ainda
 *                                  muda a cada inscrição, e isso é normal
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
        boolean amountChangedAfterPayment,
        MatchTeam team
) {
    public static ParticipantResponseDTO fromEntity(
            MatchParticipant participant,
            BigDecimal shareAmount,
            MatchStatus matchStatus
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
                matchStatus == MatchStatus.CLOSED
                        && participant.isPaid()
                        && participant.getPaidAmount().compareTo(shareAmount) != 0,
                participant.getTeam()
        );
    }
}
