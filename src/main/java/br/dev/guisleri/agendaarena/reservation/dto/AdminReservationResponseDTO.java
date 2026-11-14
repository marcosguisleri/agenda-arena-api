package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Linha da listagem de reservas do administrador. {@code customerName} e {@code customerPhone}
 * são o contato de quem reservou: o organizador nas reservas online, o cliente informado pelo
 * admin nas manuais. {@code organizerId} só existe nas online.
 */
public record AdminReservationResponseDTO(
        Long id,
        ReservationStatus status,
        ReservationOrigin origin,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        Long scheduleSlotId,
        SportFieldSummary sportField,
        Long organizerId,
        String customerName,
        String customerPhone
) {

    public record SportFieldSummary(Long id, String name) {
    }

    public static AdminReservationResponseDTO fromEntity(Reservation reservation) {
        var organizer = reservation.getOrganizer();
        var sportField = reservation.getScheduleSlot().getSportField();

        return new AdminReservationResponseDTO(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getOrigin(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                reservation.getScheduleSlot().getId(),
                new SportFieldSummary(sportField.getId(), sportField.getName()),
                organizer != null ? organizer.getId() : null,
                organizer != null ? organizer.getName() : reservation.getCustomerName(),
                organizer != null ? organizer.getPhone() : reservation.getCustomerPhone()
        );
    }
}
