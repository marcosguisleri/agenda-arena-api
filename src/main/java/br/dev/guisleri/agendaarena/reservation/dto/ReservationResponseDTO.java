package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationResponseDTO(
        Long id,
        Long organizerId,
        ReservationOrigin origin,
        String customerName,
        String customerPhone,
        Long scheduleSlotId,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        ReservationStatus status
) {
    public static ReservationResponseDTO fromEntity(Reservation reservation) {
        return new ReservationResponseDTO(
                reservation.getId(),
                reservation.getOrganizer() != null
                        ? reservation.getOrganizer().getId()
                        : null,
                reservation.getOrigin(),
                reservation.getCustomerName(),
                reservation.getCustomerPhone(),
                reservation.getScheduleSlot().getId(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPrice(),
                reservation.getStatus()
        );
    }
}
