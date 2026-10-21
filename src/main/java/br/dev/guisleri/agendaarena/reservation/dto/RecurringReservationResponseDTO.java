package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;

import java.time.LocalDate;

public record RecurringReservationResponseDTO(
        Long id,
        Long scheduleSlotId,
        Long organizerId,
        String responsibleName,
        String responsiblePhone,
        LocalDate startDate,
        LocalDate endDate
) {
    public static RecurringReservationResponseDTO fromEntity(
            RecurringReservation recurringReservation
    ) {
        return new RecurringReservationResponseDTO(
                recurringReservation.getId(),
                recurringReservation.getScheduleSlot().getId(),
                recurringReservation.getOrganizer() != null
                        ? recurringReservation.getOrganizer().getId()
                        : null,
                recurringReservation.getResponsibleName(),
                recurringReservation.getResponsiblePhone(),
                recurringReservation.getStartDate(),
                recurringReservation.getEndDate()
        );
    }
}
