package br.dev.guisleri.agendaarena.reservation.event;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Retrato imutável da reserva para os avisos por e-mail. É montado dentro da transação (lê as
 * associações lazy) e leva só dados simples: o envio acontece depois do commit, em outra thread,
 * sem sessão do Hibernate. Os campos do organizador são nulos nas reservas manuais.
 */
public record ReservationNotice(
        Long reservationId,
        Long establishmentId,
        String establishmentName,
        String sportFieldName,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        String organizerName,
        String organizerEmail,
        String organizerPhone
) {

    public static ReservationNotice from(Reservation reservation) {
        SportField sportField = reservation.getScheduleSlot().getSportField();
        User organizer = reservation.getOrganizer();

        return new ReservationNotice(
                reservation.getId(),
                sportField.getEstablishment().getId(),
                sportField.getEstablishment().getName(),
                sportField.getName(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                organizer != null ? organizer.getName() : null,
                organizer != null ? organizer.getEmail() : null,
                organizer != null ? organizer.getPhone() : null
        );
    }
}
