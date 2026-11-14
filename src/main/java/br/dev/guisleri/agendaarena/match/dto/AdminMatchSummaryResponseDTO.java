package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantCounts;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Linha da listagem de partidas do administrador (somente leitura). Não traz o link público da
 * partida: ele é a credencial de quem organiza.
 *
 * @param cancelled o horário de origem deixou de existir (reserva cancelada ou ocorrência
 *                  liberada)
 */
public record AdminMatchSummaryResponseDTO(
        Long id,
        MatchStatus status,
        boolean cancelled,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        Long scheduleSlotId,
        SportFieldSummary sportField,
        OrganizerSummary organizer,
        long fieldPlayerCount,
        long goalkeeperCount,
        long payingCount,
        long paidCount
) {

    public record SportFieldSummary(Long id, String name) {

        static SportFieldSummary from(SportField sportField) {
            return new SportFieldSummary(sportField.getId(), sportField.getName());
        }
    }

    public record OrganizerSummary(Long id, String name, String phone) {

        static OrganizerSummary from(User organizer) {
            return new OrganizerSummary(
                    organizer.getId(),
                    organizer.getName(),
                    organizer.getPhone()
            );
        }
    }

    public static AdminMatchSummaryResponseDTO from(
            Match match,
            boolean cancelled,
            MatchParticipantCounts counts
    ) {
        return new AdminMatchSummaryResponseDTO(
                match.getId(),
                match.getStatus(),
                cancelled,
                match.getDate(),
                match.getStartTime(),
                match.getEndTime(),
                match.getPrice(),
                match.getScheduleSlot().getId(),
                SportFieldSummary.from(match.getScheduleSlot().getSportField()),
                OrganizerSummary.from(match.getOrganizer()),
                counts.fieldPlayers(),
                counts.goalkeepers(),
                counts.paying(),
                counts.paid()
        );
    }
}
