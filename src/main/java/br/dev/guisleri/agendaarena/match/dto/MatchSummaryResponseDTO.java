package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.ShirtColor;
import br.dev.guisleri.agendaarena.match.service.MatchDetails;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Resumo básico da partida (organizador e link público; sem ids internos).
 *
 * @param collectedTotal soma dos valores declarados como pagos (declaração manual)
 * @param pendingTotal   soma das cotas de quem paga e ainda não declarou pagamento
 * @param pendingPlayers nomes, em ordem de inscrição, de quem ainda não pagou
 * @param withoutTeam    inscritos que não estão em nenhum time
 */
public record MatchSummaryResponseDTO(
        MatchStatus status,
        String establishmentName,
        String sportFieldName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        int participantCount,
        long payingCount,
        long paidCount,
        BigDecimal collectedTotal,
        BigDecimal pendingTotal,
        List<String> pendingPlayers,
        boolean teamsAssigned,
        List<TeamSummary> teams,
        List<TeamMemberResponseDTO> withoutTeam
) {
    private static final BigDecimal ZERO = BigDecimal.valueOf(0, 2);

    public record TeamSummary(
            MatchTeam team,
            String name,
            ShirtColor shirtColor,
            List<TeamMemberResponseDTO> members
    ) {
    }

    public static MatchSummaryResponseDTO fromDetails(MatchDetails details) {
        Match match = details.match();
        SportField sportField = match.getScheduleSlot().getSportField();
        List<MatchParticipant> participants = details.participants();
        List<MatchParticipant> pending = participants.stream()
                .filter(participant -> participant.isPaysShare() && !participant.isPaid())
                .toList();

        return new MatchSummaryResponseDTO(
                match.getStatus(),
                sportField.getEstablishment().getName(),
                sportField.getName(),
                match.getDate(),
                match.getStartTime(),
                match.getEndTime(),
                match.getPrice(),
                participants.size(),
                participants.stream().filter(MatchParticipant::isPaysShare).count(),
                participants.stream().filter(MatchParticipant::isPaid).count(),
                participants.stream()
                        .filter(MatchParticipant::isPaid)
                        .map(MatchParticipant::getPaidAmount)
                        .reduce(ZERO, BigDecimal::add),
                pending.stream()
                        .map(details::shareOf)
                        .reduce(ZERO, BigDecimal::add),
                pending.stream().map(MatchParticipant::getName).toList(),
                participants.stream().anyMatch(participant -> participant.getTeam() != null),
                Arrays.stream(MatchTeam.values())
                        .map(team -> new TeamSummary(
                                team,
                                match.getTeamName(team),
                                match.getTeamShirtColor(team),
                                members(participants, team)
                        ))
                        .toList(),
                members(participants, null)
        );
    }

    private static List<TeamMemberResponseDTO> members(
            List<MatchParticipant> participants,
            MatchTeam team
    ) {
        return participants.stream()
                .filter(participant -> participant.getTeam() == team)
                .map(TeamMemberResponseDTO::fromEntity)
                .toList();
    }
}
