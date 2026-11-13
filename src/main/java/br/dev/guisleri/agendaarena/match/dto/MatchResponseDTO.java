package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Visão do organizador: inclui o token para montar o link compartilhável.
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
        Instant createdAt
) {
    public static MatchResponseDTO fromEntity(Match match) {
        Reservation reservation = match.getReservation();
        SportField sportField = reservation.getScheduleSlot().getSportField();

        return new MatchResponseDTO(
                reservation.getId(),
                match.getPublicToken(),
                match.getStatus(),
                reservation.getStatus(),
                sportField.getEstablishment().getName(),
                sportField.getName(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                match.getCreatedAt()
        );
    }
}
