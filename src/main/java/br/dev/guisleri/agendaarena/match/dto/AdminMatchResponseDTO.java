package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.service.MatchDetails;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Partida vista pelo administrador do estabelecimento (somente leitura). Mostra quem organiza,
 * quem vem e quem já pagou, mas não traz o link público da partida (credencial do organizador).
 *
 * @param reservationId          origem reserva avulsa (nulo se for ocorrência de recorrência)
 * @param recurringReservationId origem ocorrência de recorrência (nulo se for reserva avulsa)
 * @param reservationStatus      estado da reserva de origem (nulo para ocorrência)
 * @param shareTotal             soma das cotas de quem paga (a cota de cada um muda enquanto a
 *                               partida está aberta)
 * @param paidTotal              soma do que foi declarado como pago
 */
public record AdminMatchResponseDTO(
        Long id,
        MatchStatus status,
        boolean cancelled,
        Long reservationId,
        ReservationStatus reservationStatus,
        Long recurringReservationId,
        LocalDate occurrenceDate,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        Long scheduleSlotId,
        AdminMatchSummaryResponseDTO.SportFieldSummary sportField,
        AdminMatchSummaryResponseDTO.OrganizerSummary organizer,
        Instant createdAt,
        Instant closedAt,
        Instant teamsUpdatedAt,
        long fieldPlayerCount,
        long goalkeeperCount,
        long payingCount,
        long paidCount,
        BigDecimal shareTotal,
        BigDecimal paidTotal,
        List<TeamResponseDTO> teams,
        List<ParticipantResponseDTO> participants
) {

    public static AdminMatchResponseDTO fromDetails(MatchDetails details) {
        Match match = details.match();
        Reservation reservation = match.getReservation();
        List<MatchParticipant> participants = details.participants();

        return new AdminMatchResponseDTO(
                match.getId(),
                match.getStatus(),
                details.sourceCancelled(),
                reservation != null ? reservation.getId() : null,
                reservation != null ? reservation.getStatus() : null,
                match.getRecurringReservation() != null
                        ? match.getRecurringReservation().getId()
                        : null,
                match.getOccurrenceDate(),
                match.getDate(),
                match.getStartTime(),
                match.getEndTime(),
                match.getPrice(),
                match.getScheduleSlot().getId(),
                AdminMatchSummaryResponseDTO.SportFieldSummary.from(
                        match.getScheduleSlot().getSportField()),
                AdminMatchSummaryResponseDTO.OrganizerSummary.from(match.getOrganizer()),
                match.getCreatedAt(),
                match.getClosedAt(),
                match.getTeamsUpdatedAt(),
                MatchResponseDTO.count(participants, PlayerPosition.FIELD),
                MatchResponseDTO.count(participants, PlayerPosition.GOALKEEPER),
                participants.stream().filter(MatchParticipant::isPaysShare).count(),
                participants.stream().filter(MatchParticipant::isPaid).count(),
                participants.stream()
                        .filter(MatchParticipant::isPaysShare)
                        .map(details::shareOf)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                participants.stream()
                        .filter(MatchParticipant::isPaid)
                        .map(MatchParticipant::getPaidAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                TeamResponseDTO.fromMatch(match),
                participants.stream()
                        .map(participant -> ParticipantResponseDTO.fromEntity(
                                participant,
                                details.shareOf(participant),
                                match.getStatus()
                        ))
                        .toList()
        );
    }
}
