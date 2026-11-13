package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Série recorrente vista pelo organizador dono dela ("minhas recorrências"): traz o contexto
 * (estabelecimento, quadra e horário) para o front não precisar de chamadas extras.
 */
public record MyRecurringReservationResponseDTO(
        Long id,
        Long establishmentId,
        String establishmentName,
        Long sportFieldId,
        String sportFieldName,
        Long scheduleSlotId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        String responsibleName,
        LocalDate startDate,
        LocalDate endDate
) {
    public static MyRecurringReservationResponseDTO fromEntity(
            RecurringReservation recurringReservation
    ) {
        ScheduleSlot slot = recurringReservation.getScheduleSlot();

        return new MyRecurringReservationResponseDTO(
                recurringReservation.getId(),
                slot.getSportField().getEstablishment().getId(),
                slot.getSportField().getEstablishment().getName(),
                slot.getSportField().getId(),
                slot.getSportField().getName(),
                slot.getId(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getPrice(),
                recurringReservation.getResponsibleName(),
                recurringReservation.getStartDate(),
                recurringReservation.getEndDate()
        );
    }
}
