package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.service.MatchDetails;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Visão do organizador: inclui o token para montar o link compartilhável e os ids dos
 * participantes para editar a lista.
 */
public record MatchResponseDTO(
        Long reservationId,
        String publicToken,
        MatchStatus status,
        ReservationStatus reservationStatus,
        String establishmentName,
        String sportFieldName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        String pixKey,
        Instant createdAt,
        Instant closedAt,
        Instant teamsUpdatedAt,
        long fieldPlayerCount,
        long goalkeeperCount,
        long payingCount,
        List<TeamResponseDTO> teams,
        List<ParticipantResponseDTO> participants
) {
    public static MatchResponseDTO fromDetails(MatchDetails details) {
        Match match = details.match();
        Reservation reservation = match.getReservation();
        SportField sportField = match.getScheduleSlot().getSportField();
        List<MatchParticipant> participants = details.participants();

        return new MatchResponseDTO(
                reservation != null ? reservation.getId() : null,
                match.getPublicToken(),
                match.getStatus(),
                reservation != null ? reservation.getStatus() : null,
                sportField.getEstablishment().getName(),
                sportField.getName(),
                match.getDate(),
                match.getStartTime(),
                match.getEndTime(),
                match.getPrice(),
                match.getPixKey(),
                match.getCreatedAt(),
                match.getClosedAt(),
                match.getTeamsUpdatedAt(),
                count(participants, PlayerPosition.FIELD),
                count(participants, PlayerPosition.GOALKEEPER),
                participants.stream().filter(MatchParticipant::isPaysShare).count(),
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

    static long count(List<MatchParticipant> participants, PlayerPosition position) {
        return participants.stream()
                .filter(participant -> participant.getPosition() == position)
                .count();
    }
}
