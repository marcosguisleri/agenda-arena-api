package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Visão de quem tem o link (sem conta). Sem ids internos, sem dados de contato do organizador e
 * sem o estado detalhado da reserva: só se ela foi cancelada.
 */
public record PublicMatchResponseDTO(
        MatchStatus status,
        boolean reservationCancelled,
        String establishmentName,
        String sportFieldName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price
) {
    public static PublicMatchResponseDTO fromEntity(Match match) {
        Reservation reservation = match.getReservation();
        SportField sportField = reservation.getScheduleSlot().getSportField();

        return new PublicMatchResponseDTO(
                match.getStatus(),
                reservation.getStatus() == ReservationStatus.CANCELLED,
                sportField.getEstablishment().getName(),
                sportField.getName(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice()
        );
    }
}
