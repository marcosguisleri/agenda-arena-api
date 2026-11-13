package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.service.MatchDetails;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Visão de quem tem o link (sem conta). Sem ids internos, sem dados de contato do organizador e
 * sem o estado detalhado da origem: só se o horário foi cancelado ({@code cancelled}).
 */
public record PublicMatchResponseDTO(
        MatchStatus status,
        boolean cancelled,
        String establishmentName,
        String sportFieldName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        String pixKey,
        Instant teamsUpdatedAt,
        long fieldPlayerCount,
        long goalkeeperCount,
        long payingCount,
        List<TeamResponseDTO> teams,
        List<PublicParticipantResponseDTO> participants
) {
    public static PublicMatchResponseDTO fromDetails(MatchDetails details) {
        Match match = details.match();
        SportField sportField = match.getScheduleSlot().getSportField();
        List<MatchParticipant> participants = details.participants();

        return new PublicMatchResponseDTO(
                match.getStatus(),
                details.sourceCancelled(),
                sportField.getEstablishment().getName(),
                sportField.getName(),
                match.getDate(),
                match.getStartTime(),
                match.getEndTime(),
                match.getPrice(),
                match.getPixKey(),
                match.getTeamsUpdatedAt(),
                MatchResponseDTO.count(participants, PlayerPosition.FIELD),
                MatchResponseDTO.count(participants, PlayerPosition.GOALKEEPER),
                participants.stream().filter(MatchParticipant::isPaysShare).count(),
                TeamResponseDTO.fromMatch(match),
                participants.stream()
                        .map(participant -> PublicParticipantResponseDTO.fromEntity(
                                participant,
                                details.shareOf(participant)
                        ))
                        .toList()
        );
    }
}
