package br.dev.guisleri.agendaarena.schedule.dto;

import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlotResponseDTO(
        Long id,
        Long sportFieldId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal price,
        boolean active
) {
    public static ScheduleSlotResponseDTO fromEntity(ScheduleSlot scheduleSlot) {
        return new ScheduleSlotResponseDTO(
                scheduleSlot.getId(),
                scheduleSlot.getSportField().getId(),
                scheduleSlot.getDayOfWeek(),
                scheduleSlot.getStartTime(),
                scheduleSlot.getEndTime(),
                scheduleSlot.getPrice(),
                scheduleSlot.isActive()
        );
    }
}
