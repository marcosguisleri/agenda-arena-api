package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.model.RecurringReservationListStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Linha da listagem de séries recorrentes do administrador. {@code organizerId} é nulo quando a
 * série ainda não foi vinculada a um organizador com conta (sem ele não há partida).
 */
public record AdminRecurringReservationResponseDTO(
        Long id,
        RecurringReservationListStatus status,
        Long scheduleSlotId,
        AdminReservationResponseDTO.SportFieldSummary sportField,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        LocalDate startDate,
        LocalDate endDate,
        Long organizerId,
        String responsibleName,
        String responsiblePhone
) {

    public static AdminRecurringReservationResponseDTO fromEntity(
            RecurringReservation recurringReservation,
            LocalDate today
    ) {
        var slot = recurringReservation.getScheduleSlot();
        var sportField = slot.getSportField();
        var organizer = recurringReservation.getOrganizer();

        return new AdminRecurringReservationResponseDTO(
                recurringReservation.getId(),
                statusOn(recurringReservation, today),
                slot.getId(),
                new AdminReservationResponseDTO.SportFieldSummary(
                        sportField.getId(),
                        sportField.getName()
                ),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getPrice(),
                recurringReservation.getStartDate(),
                recurringReservation.getEndDate(),
                organizer != null ? organizer.getId() : null,
                recurringReservation.getResponsibleName(),
                recurringReservation.getResponsiblePhone()
        );
    }

    private static RecurringReservationListStatus statusOn(
            RecurringReservation recurringReservation,
            LocalDate today
    ) {
        if (recurringReservation.getStartDate().isAfter(today)) {
            return RecurringReservationListStatus.UPCOMING;
        }

        if (recurringReservation.getEndDate() != null
                && recurringReservation.getEndDate().isBefore(today)) {
            return RecurringReservationListStatus.ENDED;
        }

        return RecurringReservationListStatus.ACTIVE;
    }
}
